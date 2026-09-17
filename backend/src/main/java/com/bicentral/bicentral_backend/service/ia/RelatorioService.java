package com.bicentral.bicentral_backend.service.ia;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bicentral.bicentral_backend.dto.relatorio.AcaoAnalisadaDTO;
import com.bicentral.bicentral_backend.dto.relatorio.AcaoRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DepartamentoParceiroDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DistribuicaoExecucaoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.IndicadorRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.JustificativaAcaoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.PontoAcompanhamentoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioConteudoIADTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.TarefaResponsavelDTO;

import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RelatorioService {

    private final JdbcTemplate jdbcTemplate;
    private final AgenteRelatorio agenteRelatorio;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RelatorioService(JdbcTemplate jdbcTemplate, AgenteRelatorio agenteRelatorio) {
        this.jdbcTemplate = jdbcTemplate;
        this.agenteRelatorio = agenteRelatorio;
    }

    @PostConstruct
    public void prepararTabelaRelatorios() {
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS formato VARCHAR(10) DEFAULT 'DOCX'");
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS texto_relatorio TEXT");
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS pdf_url TEXT");
    }

    public Long solicitarRelatorio(Long usuarioId, String departamento, String tipo) {
        return solicitarRelatorio(usuarioId, departamento, tipo, "DOCX");
    }

    public Long solicitarRelatorio(Long usuarioId, String departamento, String tipo, String formato) {
        // PDI hoje só tem uma carga estática/manual, não a integração real da API, que ainda não
        // foi ligada. Força PAT aqui pra fechar TODA entrada possível (chat, e também o endpoint
        // REST direto em RelatorioController, que aceita "tipo" livre) — sem isso o chat já
        // recusa falar de PDI, mas um relatório ainda conseguia sair com esse dado estático
        // desatualizado sem avisar ninguém. Reverter isso (tirar essa linha) quando a integração
        // real da API do PDI estiver pronta.
        tipo = "PAT";
        String formatoFinal = normalizarFormato(formato);

        Long idExistente = buscarRelatorioEmProcessamentoRecente(usuarioId, departamento, tipo, formatoFinal);
        if (idExistente != null) {
            System.out.println(">>> RELATORIO reaproveitado (já em processamento): id=" + idExistente + ", departamento=" + departamento + ", tipo=" + tipo);
            return idExistente;
        }

        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO relatorios_gerados (usuario_id, departamento, tipo, formato, status)
            VALUES (?, ?, ?, ?, 'PROCESSANDO')
            RETURNING id
            """, Long.class, usuarioId, departamento, tipo, formatoFinal);

        System.out.println(">>> RELATORIO solicitado: id=" + id + ", departamento=" + departamento + ", tipo=" + tipo + ", formato=" + formatoFinal);
        processarRelatorioAsync(id, departamento, tipo, formatoFinal);
        return id;
    }

    /**
     * Evita gerar relatórios duplicados quando a mesma solicitação chega várias vezes
     * seguidas em pouco tempo (ex: o agente chamando a ferramenta repetidamente).
     */
    private Long buscarRelatorioEmProcessamentoRecente(Long usuarioId, String departamento, String tipo, String formato) {
        List<Long> encontrados = jdbcTemplate.queryForList("""
            SELECT id FROM relatorios_gerados
            WHERE usuario_id = ? AND departamento = ? AND tipo = ? AND formato = ?
                AND status = 'PROCESSANDO' AND criado_em > NOW() - INTERVAL '60 seconds'
            ORDER BY criado_em DESC
            LIMIT 1
            """, Long.class, usuarioId, departamento, tipo, formato);
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    public Map<String, Object> buscarStatus(Long id) {
        return jdbcTemplate.queryForMap(
            "SELECT id, status, arquivo_url, mensagem_erro, departamento, tipo, COALESCE(formato, 'DOCX') AS formato, criado_em, concluido_em FROM relatorios_gerados WHERE id = ?",
            id
        );
    }

    public boolean excluirRelatorio(Long id, Long usuarioId) {
        List<Map<String, Object>> encontrados = jdbcTemplate.queryForList("""
            SELECT arquivo_url, pdf_url
            FROM relatorios_gerados
            WHERE id = ? AND usuario_id = ?
            """, id, usuarioId);

        if (encontrados.isEmpty()) {
            return false;
        }

        String arquivoUrl = (String) encontrados.get(0).get("arquivo_url");
        String nomeArquivo = extrairNomeArquivoDoBucket(arquivoUrl);
        if (nomeArquivo != null) {
            try {
                excluirArquivoDoBucket(nomeArquivo);
            } catch (Exception e) {
                System.err.println(">>> RELATORIO aviso: falha ao excluir arquivo do bucket id=" + id + ": " + e.getMessage());
            }
        }

        String pdfUrl = (String) encontrados.get(0).get("pdf_url");
        String nomePdf = extrairNomeArquivoDoBucket(pdfUrl);
        if (nomePdf != null) {
            try {
                excluirArquivoDoBucket(nomePdf);
            } catch (Exception e) {
                System.err.println(">>> RELATORIO aviso: falha ao excluir PDF do bucket id=" + id + ": " + e.getMessage());
            }
        }

        jdbcTemplate.update("DELETE FROM relatorios_gerados WHERE id = ? AND usuario_id = ?", id, usuarioId);
        return true;
    }

    public Map<String, Object> gerarOuBuscarPdf(Long id, Long usuarioId) {
        List<Map<String, Object>> encontrados = jdbcTemplate.queryForList("""
            SELECT id, departamento, tipo, status, texto_relatorio, pdf_url
            FROM relatorios_gerados
            WHERE id = ? AND usuario_id = ?
            """, id, usuarioId);

        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("Relatório não encontrado.");
        }

        Map<String, Object> relatorio = encontrados.get(0);
        if (!"PRONTO".equals(relatorio.get("status"))) {
            throw new IllegalStateException("Relatório ainda não está pronto.");
        }

        String pdfUrl = (String) relatorio.get("pdf_url");
        if (pdfUrl != null && !pdfUrl.isBlank()) {
            return Map.of("pdf_url", pdfUrl);
        }

        String textoRelatorio = (String) relatorio.get("texto_relatorio");
        if (textoRelatorio == null || textoRelatorio.isBlank()) {
            throw new IllegalStateException("Este relatório não possui dados armazenados para gerar PDF.");
        }

        try {
            String departamento = (String) relatorio.get("departamento");
            RelatorioEstruturadoDTO estruturado = MAPPER.readValue(textoRelatorio, RelatorioEstruturadoDTO.class);
            byte[] pdfBytes = gerarPdf(estruturado);
            String novaPdfUrl = enviarParaBucket(pdfBytes, departamento, id, "pdf", "application/pdf");

            jdbcTemplate.update("UPDATE relatorios_gerados SET pdf_url = ? WHERE id = ? AND usuario_id = ?", novaPdfUrl, id, usuarioId);
            return Map.of("pdf_url", novaPdfUrl);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar PDF do relatório: " + e.getMessage(), e);
        }
    }

    @Async
    public void processarRelatorioAsync(Long id, String departamento, String tipo, String formato) {
        try {
            System.out.println(">>> RELATORIO processando id=" + id);

            DadosQuantitativosRelatorio dados = montarDadosQuantitativos(departamento, tipo);
            String prompt = montarPromptParaIA(departamento, tipo, dados.piores());
            RelatorioConteudoIADTO conteudo = agenteRelatorio.gerarConteudoRelatorio(prompt);

            RelatorioEstruturadoDTO estruturado = new RelatorioEstruturadoDTO(
                    departamento,
                    tipo,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    conteudo.resumoExecutivo(),
                    conteudo.leituraCenario(),
                    dados.indicadores(),
                    dados.distribuicao(),
                    combinarAnaliseComJustificativas(departamento, dados.piores(), conteudo.analiseMenorExecucao()),
                    dados.melhores(),
                    conteudo.pontosDeAcompanhamento());

            byte[] arquivoBytes;
            String extensao;
            String contentType;
            if ("PDF".equalsIgnoreCase(formato)) {
                arquivoBytes = gerarPdf(estruturado);
                extensao = "pdf";
                contentType = "application/pdf";
            } else {
                arquivoBytes = gerarDocx(estruturado);
                extensao = "docx";
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            String urlArquivo = enviarParaBucket(arquivoBytes, departamento, id, extensao, contentType);
            String jsonRelatorio = MAPPER.writeValueAsString(estruturado);

            jdbcTemplate.update("""
                UPDATE relatorios_gerados
                SET status = 'PRONTO', arquivo_url = ?, texto_relatorio = ?, formato = ?, concluido_em = NOW()
                WHERE id = ?
                """, urlArquivo, jsonRelatorio, normalizarFormato(formato), id);

            System.out.println(">>> RELATORIO concluído id=" + id + ", url=" + urlArquivo);

        } catch (Exception e) {
            System.err.println(">>> RELATORIO ERRO id=" + id + ": " + e.getMessage());
            e.printStackTrace();
            jdbcTemplate.update("""
                UPDATE relatorios_gerados
                SET status = 'ERRO', mensagem_erro = ?
                WHERE id = ?
                """, e.getMessage(), id);
        }
    }

    /**
     * Pareia por índice as ações de menor execução (dado exato, vindo do SQL) com as
     * justificativas que a IA produziu pra cada uma, e enriquece cada uma com as tarefas
     * operacionais do próprio departamento e a comparação com outros departamentos que
     * compartilham a mesma ação — nenhuma dessas duas coisas passa pela IA.
     * Se a IA devolver uma quantidade diferente da recebida, pareia só até o menor
     * tamanho em vez de quebrar.
     */
    private List<AcaoAnalisadaDTO> combinarAnaliseComJustificativas(String departamento, List<AcaoComCodigo> piores, List<JustificativaAcaoDTO> justificativas) {
        int total = Math.min(piores.size(), justificativas.size());

        List<String> codigos = piores.stream()
                .limit(total)
                .map(AcaoComCodigo::codigo)
                .filter(c -> c != null && !c.isBlank())
                .toList();

        // Antes: 1 query de tarefas + 1 query de departamentos parceiros POR ação (até 30 idas ao
        // banco pras 15 piores ações). Agora: busca tudo de uma vez com IN (...) e agrupa em Java.
        Map<String, List<TarefaResponsavelDTO>> tarefasPorCodigo = buscarTarefasPorAcoes(departamento, codigos);
        Map<String, List<DepartamentoParceiroDTO>> parceirosPorCodigo = buscarOutrosDepartamentosPorAcoes(codigos, departamento);

        List<AcaoAnalisadaDTO> resultado = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            AcaoComCodigo acao = piores.get(i);
            JustificativaAcaoDTO justificativa = justificativas.get(i);
            List<TarefaResponsavelDTO> tarefas = tarefasPorCodigo.getOrDefault(acao.codigo(), List.of());
            List<DepartamentoParceiroDTO> outrosDepartamentos = parceirosPorCodigo.getOrDefault(acao.codigo(), List.of());
            resultado.add(new AcaoAnalisadaDTO(
                    acao.acao(),
                    justificativa.tema(),
                    acao.percentual(),
                    tarefas,
                    outrosDepartamentos,
                    justificativa.justificativa(),
                    calcularPrecisaAtencao(acao.percentual(), tarefas, outrosDepartamentos)));
        }
        return resultado;
    }

    private static final int DIAS_LIMITE_ATENCAO = 60;
    private static final double GAP_LIMITE_ATENCAO = 40.0;

    /**
     * "Precisa atenção" é calculado aqui, não pela IA — ela não sabe a data de hoje nem
     * o prazo real das tarefas (só recebe título e percentual). Dois sinais objetivos:
     * prazo da tarefa mais próxima vencendo em até DIAS_LIMITE_ATENCAO dias, ou um
     * departamento parceiro na mesma ação GAP_LIMITE_ATENCAO pontos % à frente.
     */
    private boolean calcularPrecisaAtencao(Double percentual, List<TarefaResponsavelDTO> tarefas, List<DepartamentoParceiroDTO> outrosDepartamentos) {
        boolean prazoApertado = tarefas.stream()
                .map(TarefaResponsavelDTO::prazo)
                .filter(java.util.Objects::nonNull)
                .anyMatch(prazoStr -> {
                    try {
                        java.time.LocalDate prazo = java.time.LocalDate.parse(prazoStr, FORMATO_PRAZO);
                        return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), prazo) <= DIAS_LIMITE_ATENCAO;
                    } catch (Exception e) {
                        return false;
                    }
                });

        boolean atrasDeParceiro = percentual != null && outrosDepartamentos.stream()
                .anyMatch(d -> d.percentual() != null && d.percentual() - percentual >= GAP_LIMITE_ATENCAO);

        return prazoApertado || atrasDeParceiro;
    }

    /**
     * Tarefas operacionais do próprio departamento (pat_tarefas), com responsável e prazo, pra
     * TODAS as ações recebidas de uma vez (1 query com IN, não 1 query por ação) — agrupadas por
     * código da ação, no máximo 3 tarefas por código (mais recentes/próximas primeiro).
     */
    private Map<String, List<TarefaResponsavelDTO>> buscarTarefasPorAcoes(String departamento, List<String> codigos) {
        if (codigos.isEmpty()) {
            return Map.of();
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(codigos.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.add("%" + departamento.trim() + "%");
            params.addAll(codigos);

            String sql = "SELECT " +
                    "substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao, " +
                    "dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa, " +
                    "dados_completos->>'DESCRIÇÃO DA TAREFA' AS descricao_tarefa, " +
                    "dados_completos->>'Responsável' AS responsavel, " +
                    "to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final " +
                    "FROM pat_tarefas " +
                    "WHERE departamento ILIKE ? " +
                    "AND substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') IN (" + placeholders + ") " +
                    "ORDER BY to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') ASC NULLS LAST";

            List<Map<String, Object>> tarefas = jdbcTemplate.queryForList(sql, params.toArray());

            // O "TÍTULO DA TAREFA" é um rótulo genérico compartilhado por várias tarefas reais da
            // mesma ação (ex: duas pessoas diferentes com pedaços distintos do mesmo trabalho) — só
            // a "DESCRIÇÃO DA TAREFA" diferencia de verdade uma da outra. Sem ela, tarefas
            // completamente diferentes (ids, descrições e às vezes até responsáveis diferentes)
            // aparecem no relatório como se fossem a mesma linha duplicada.
            Map<String, List<TarefaResponsavelDTO>> porCodigo = new java.util.LinkedHashMap<>();
            for (Map<String, Object> t : tarefas) {
                String codigo = (String) t.get("codigo_acao");
                List<TarefaResponsavelDTO> lista = porCodigo.computeIfAbsent(codigo, k -> new ArrayList<>());
                if (lista.size() < 3) {
                    lista.add(new TarefaResponsavelDTO(
                            (String) t.get("titulo_tarefa"),
                            (String) t.get("descricao_tarefa"),
                            (String) t.get("responsavel"),
                            formatarPrazo((java.sql.Date) t.get("data_final"))));
                }
            }
            return porCodigo;
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar tarefas em lote das ações: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * Outros departamentos que também respondem por cada ação, com o percentual de execução —
     * pra TODAS as ações recebidas de uma vez (1 query com IN, não 1 query por ação).
     */
    private Map<String, List<DepartamentoParceiroDTO>> buscarOutrosDepartamentosPorAcoes(List<String> codigos, String departamentoAtual) {
        if (codigos.isEmpty()) {
            return Map.of();
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(codigos.size(), "?"));
            List<Object> params = new ArrayList<>(codigos);
            params.add("%" + departamentoAtual.trim() + "%");

            String sql = "SELECT codigo_acao, departamento, ROUND(percentual_execucao * 100, 2) AS percentual " +
                    "FROM pat_execucao_departamento " +
                    "WHERE codigo_acao IN (" + placeholders + ") AND departamento NOT ILIKE ? " +
                    "ORDER BY codigo_acao, percentual_execucao DESC";

            List<Map<String, Object>> linhas = jdbcTemplate.queryForList(sql, params.toArray());

            Map<String, List<DepartamentoParceiroDTO>> porCodigo = new java.util.LinkedHashMap<>();
            for (Map<String, Object> row : linhas) {
                String codigo = (String) row.get("codigo_acao");
                porCodigo.computeIfAbsent(codigo, k -> new ArrayList<>())
                        .add(new DepartamentoParceiroDTO((String) row.get("departamento"), toDouble(row.get("percentual"))));
            }
            return porCodigo;
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar departamentos parceiros em lote: " + e.getMessage());
            return Map.of();
        }
    }

    private static final java.time.format.DateTimeFormatter FORMATO_PRAZO = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatarPrazo(java.sql.Date data) {
        return data != null ? data.toLocalDate().format(FORMATO_PRAZO) : null;
    }

    private String normalizarFormato(String formato) {
        if (formato == null || formato.isBlank()) {
            return "DOCX";
        }
        return "PDF".equalsIgnoreCase(formato.trim()) ? "PDF" : "DOCX";
    }

    /**
     * Ação com código isolado — usado só internamente pra poder cruzar com pat_tarefas
     * (tarefas operacionais) e com outros departamentos que compartilham a mesma ação.
     * Não faz parte do JSON persistido (isso é o AcaoAnalisadaDTO, montado depois).
     */
    private record AcaoComCodigo(String codigo, String acao, Double percentual) {
    }

    private record DadosQuantitativosRelatorio(
            List<IndicadorRelatorioDTO> indicadores,
            DistribuicaoExecucaoDTO distribuicao,
            List<AcaoComCodigo> piores,
            List<AcaoRelatorioDTO> melhores) {
    }

    private static final java.time.format.DateTimeFormatter FORMATO_DATA_SNAPSHOT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Compara a média geral atual com o snapshot mais recente ANTERIOR a hoje (ver
     * RankingSnapshotJob, que grava um snapshot por departamento por dia) — devolve algo como
     * " (+2,01pp desde 16/09/2026)" pra completar o valor do indicador, ou string vazia se não
     * existe snapshot anterior pra comparar (departamento novo, ou só sincronizado hoje).
     * Sinal (+/-), não seta — no PDF a fonte padrão (WinAnsiEncoding) não tem glifo de seta, e
     * normalizarTextoPdf() troca qualquer caractere fora do Latin-1 por "-", mascarando o sinal.
     */
    private String buscarTendencia(String departamento, double mediaAtual) {
        try {
            List<Map<String, Object>> anterior = jdbcTemplate.queryForList("""
                SELECT media_execucao_pct, data_snapshot
                FROM ranking_pat_snapshots
                WHERE departamento ILIKE ? AND data_snapshot < CURRENT_DATE
                ORDER BY data_snapshot DESC
                LIMIT 1
                """, "%" + departamento.trim() + "%");

            if (anterior.isEmpty()) {
                return "";
            }

            double mediaAnterior = toDouble(anterior.get(0).get("media_execucao_pct"));
            java.sql.Date dataSnapshot = (java.sql.Date) anterior.get(0).get("data_snapshot");
            double diferenca = mediaAtual - mediaAnterior;

            String sinal = diferenca > 0.005 ? "+" : diferenca < -0.005 ? "-" : "";
            String dataFormatada = dataSnapshot.toLocalDate().format(FORMATO_DATA_SNAPSHOT);
            return String.format(new java.util.Locale("pt", "BR"), " (%s%.2f pontos percentuais desde %s)", sinal, Math.abs(diferenca), dataFormatada);
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar tendência de execução: " + e.getMessage());
            return "";
        }
    }

    /**
     * Busca os dados exatos do banco (indicadores, piores e melhores ações) — nada disso
     * passa pela IA, é montado direto em Java pra garantir que os números batem com o banco.
     */
    private DadosQuantitativosRelatorio montarDadosQuantitativos(String departamento, String tipo) {
        if ("PDI".equalsIgnoreCase(tipo)) {
            List<Map<String, Object>> acoes = jdbcTemplate.queryForList("""
                SELECT codigo, titulo, ROUND(percentual_pdi, 2) AS percentual
                FROM acoes_pdi
                WHERE estrutura = 'Ação' AND departamentos ILIKE ?
                ORDER BY percentual_pdi ASC
                """, "%" + departamento.trim() + "%");

            List<AcaoComCodigo> ordenadas = acoes.stream()
                    .map(row -> new AcaoComCodigo(
                            (String) row.get("codigo"),
                            "[" + row.get("codigo") + "] " + truncarTitulo((String) row.get("titulo")),
                            toDouble(row.get("percentual"))))
                    .toList();

            double media = ordenadas.stream().mapToDouble(AcaoComCodigo::percentual).average().orElse(0);
            List<IndicadorRelatorioDTO> indicadores = List.of(
                    new IndicadorRelatorioDTO("Total de Ações no PDI", String.valueOf(ordenadas.size())),
                    new IndicadorRelatorioDTO("Média Geral de Execução Acumulada", formatarPercentual(media)));

            List<AcaoComCodigo> piores = ordenadas.stream().limit(15).toList();
            List<AcaoRelatorioDTO> melhores = ordenadas.stream()
                    .sorted(java.util.Comparator.comparingDouble(AcaoComCodigo::percentual).reversed())
                    .limit(15)
                    .map(a -> new AcaoRelatorioDTO(a.acao(), a.percentual()))
                    .toList();
            DistribuicaoExecucaoDTO distribuicaoPdi = new DistribuicaoExecucaoDTO(
                    (int) ordenadas.stream().filter(a -> a.percentual() <= 0).count(),
                    (int) ordenadas.stream().filter(a -> a.percentual() > 0 && a.percentual() < 100).count(),
                    (int) ordenadas.stream().filter(a -> a.percentual() >= 100).count());
            return new DadosQuantitativosRelatorio(indicadores, distribuicaoPdi, piores, melhores);
        }

        // PAT (e COMPARATIVO, que por enquanto usa a mesma visão de execução do ano corrente)
        Map<String, Object> resumo = jdbcTemplate.queryForMap("""
            SELECT
                COUNT(*) AS total_acoes,
                ROUND(AVG(percentual_execucao) * 100, 2) AS media_geral,
                COUNT(*) FILTER (WHERE percentual_execucao = 0) AS zeradas,
                COUNT(*) FILTER (WHERE percentual_execucao > 0 AND percentual_execucao < 1) AS em_andamento,
                COUNT(*) FILTER (WHERE percentual_execucao >= 1) AS concluidas
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            """, "%" + departamento.trim() + "%");

        int totalAcoes = ((Number) resumo.get("total_acoes")).intValue();
        List<IndicadorRelatorioDTO> indicadores = new ArrayList<>();
        indicadores.add(new IndicadorRelatorioDTO("Total de Ações no PAT", String.valueOf(totalAcoes)));
        double mediaGeral = toDouble(resumo.get("media_geral"));
        indicadores.add(new IndicadorRelatorioDTO("Média Geral de Execução", formatarPercentual(mediaGeral) + buscarTendencia(departamento, mediaGeral)));
        indicadores.add(new IndicadorRelatorioDTO("Ações Concluídas", formatarContagem(resumo.get("concluidas"), totalAcoes)));
        indicadores.add(new IndicadorRelatorioDTO("Ações em Andamento", formatarContagem(resumo.get("em_andamento"), totalAcoes)));
        indicadores.add(new IndicadorRelatorioDTO("Ações Zeradas (Não Iniciadas)", formatarContagem(resumo.get("zeradas"), totalAcoes)));

        List<Map<String, Object>> piores = jdbcTemplate.queryForList("""
            SELECT codigo_acao, titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            ORDER BY percentual_execucao ASC
            LIMIT 15
            """, "%" + departamento.trim() + "%");

        List<Map<String, Object>> melhores = jdbcTemplate.queryForList("""
            SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            ORDER BY percentual_execucao DESC
            LIMIT 15
            """, "%" + departamento.trim() + "%");

        DistribuicaoExecucaoDTO distribuicao = new DistribuicaoExecucaoDTO(
                ((Number) resumo.get("zeradas")).intValue(),
                ((Number) resumo.get("em_andamento")).intValue(),
                ((Number) resumo.get("concluidas")).intValue());

        return new DadosQuantitativosRelatorio(
                indicadores,
                distribuicao,
                piores.stream().map(row -> new AcaoComCodigo(
                        (String) row.get("codigo_acao"),
                        truncarTitulo((String) row.get("titulo_acao")),
                        toDouble(row.get("percentual")))).toList(),
                melhores.stream().map(row -> new AcaoRelatorioDTO(truncarTitulo((String) row.get("titulo_acao")), toDouble(row.get("percentual")))).toList());
    }

    // titulo_acao vem do dado bruto com o nome completo do departamento colado após " | " — redundante
    // no relatório porque ele já é de um único departamento (repetir isso em toda ação, até 15x, é o
    // que deixa o texto poluído/estranho).
    private String truncarTitulo(String tituloAcao) {
        if (tituloAcao == null) return "";
        int separador = tituloAcao.indexOf(" | ");
        return separador >= 0 ? tituloAcao.substring(0, separador) : tituloAcao;
    }

    private static final java.util.regex.Pattern PADRAO_CODIGO_ACAO =
            java.util.regex.Pattern.compile("^([A-Za-zÀ-ÿ]+ [0-9]+(?:\\.[0-9]+)*)");

    /** Extrai só o código de uma string "CÓDIGO - Título..." (formato de AcaoComCodigo/AcaoAnalisadaDTO.acao()). */
    private String codigoDaAcao(String acao) {
        if (acao == null) return "";
        java.util.regex.Matcher m = PADRAO_CODIGO_ACAO.matcher(acao);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Tema mecânico (sem IA) pra ações que não passam pelo agente de relatório — hoje só os
     * destaques positivos (calculados puramente em Java, ver montarDadosQuantitativos). Só tira
     * o código do início; diferente do "tema" da IA, não encurta o título, então ainda pode
     * ficar longo — é um degrau abaixo do tema real, não uma reprodução dele.
     */
    private String temaSemCodigo(String acao, String codigo) {
        if (acao == null) return "";
        if (codigo == null || codigo.isBlank()) return acao;
        return acao.replaceFirst("^" + java.util.regex.Pattern.quote(codigo) + "\\s*-\\s*", "");
    }

    /**
     * Monta o prompt enxuto pra IA — só as ações que precisam de justificativa qualitativa.
     * Indicadores e destaques já são calculados em Java, não precisam ir pro modelo.
     */
    private String montarPromptParaIA(String departamento, String tipo, List<AcaoComCodigo> piores) {
        StringBuilder sb = new StringBuilder();
        sb.append("DEPARTAMENTO: ").append(departamento).append("\n");
        sb.append("TIPO DE PLANO: ").append(tipo).append("\n\n");
        sb.append("AÇÕES DE MENOR EXECUÇÃO (analise cada uma, na mesma ordem):\n");
        for (AcaoComCodigo acao : piores) {
            sb.append("- ").append(acao.acao()).append(": ").append(acao.percentual()).append("%\n");
        }
        return sb.toString();
    }

    private double toDouble(Object valor) {
        return valor == null ? 0.0 : ((Number) valor).doubleValue();
    }

    private String formatarPercentual(double valor) {
        return String.format(new java.util.Locale("pt", "BR"), "%.2f%%", valor);
    }

    private String formatarContagem(Object contagem, int total) {
        int n = ((Number) contagem).intValue();
        double percentual = total == 0 ? 0 : (n * 100.0 / total);
        return n + " (" + formatarPercentual(percentual) + ")";
    }

    private static final int MAX_DEPARTAMENTOS_EXIBIDOS = 3;

    /** Lista já vem ordenada por percentual DESC — mostra só os mais à frente, resume o resto. */
    private String formatarComparativoDepartamentos(List<DepartamentoParceiroDTO> outrosDepartamentos) {
        String principais = outrosDepartamentos.stream()
                .limit(MAX_DEPARTAMENTOS_EXIBIDOS)
                .map(d -> d.departamento() + " (" + formatarPercentual(d.percentual()) + ")")
                .collect(java.util.stream.Collectors.joining(", "));

        int restantes = outrosDepartamentos.size() - MAX_DEPARTAMENTOS_EXIBIDOS;
        return restantes > 0 ? principais + " e mais " + restantes + " departamento(s)" : principais;
    }

    /**
     * O "título" da tarefa é um rótulo genérico, repetido por várias tarefas reais e distintas da
     * mesma ação (ex: duas pessoas com pedaços diferentes do mesmo trabalho) — a "descrição" é o
     * que de fato diferencia uma tarefa da outra no relatório. Sem isso, tarefas diferentes com o
     * mesmo título pareciam a mesma linha duplicada.
     */
    private String textoTarefa(TarefaResponsavelDTO t) {
        return t.descricao() != null && !t.descricao().isBlank() ? t.descricao() : t.titulo();
    }

    private static final String COR_DESTAQUE = "1F4E79";
    // Os "melhores" já vêm limitados a 15 da consulta (montarDadosQuantitativos) — mas mostrar
    // as 15 na tabela de Desempenhos de Destaque deixa a seção comprida, com títulos sem tema de
    // IA (só o código mecanicamente removido, ver temaSemCodigo) que quebram em várias linhas.
    // Corta a exibição mais cedo, com uma nota pro restante, sem perder o dado (o relatório em
    // JSON continua com a lista completa em destaquesPositivos()).
    private static final int LIMITE_DESTAQUES_EXIBIDOS = 8;

    private byte[] gerarDocx(RelatorioEstruturadoDTO r) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            docxCapa(document, r);

            docxSecao(document, "01 · Visão Executiva");
            docxNumerosGrandes(document, r);
            docxDistribuicao(document, r.distribuicao());

            docxSecao(document, "02 · Pontos de Atenção");
            docxTabelaAcoes(document, r.analiseMenorExecucao().stream()
                    .map(a -> new LinhaTabelaAcao(codigoDaAcao(a.acao()), a.tema(), a.percentual()))
                    .toList(), COR_ATENCAO);

            docxSecao(document, "03 · Desempenhos de Destaque");
            docxTabelaAcoes(document, r.destaquesPositivos().stream()
                    .limit(LIMITE_DESTAQUES_EXIBIDOS)
                    .map(a -> new LinhaTabelaAcao(codigoDaAcao(a.acao()), temaSemCodigo(a.acao(), codigoDaAcao(a.acao())), a.percentual()))
                    .toList(), COR_POSITIVO);
            if (r.destaquesPositivos().size() > LIMITE_DESTAQUES_EXIBIDOS) {
                docxParagrafo(document, "+ " + (r.destaquesPositivos().size() - LIMITE_DESTAQUES_EXIBIDOS) + " outras ações com bom desempenho nesta unidade.");
            }

            docxSecao(document, "04 · Leitura do Cenário");
            docxParagrafo(document, r.resumoExecutivo());
            for (String insight : r.leituraCenario()) {
                XWPFParagraph p = document.createParagraph();
                p.setSpacingAfter(60);
                XWPFRun run = p.createRun();
                run.setText("• " + insight);
                run.setFontSize(11);
                run.setColor(COR_TEXTO_CORPO);
            }

            docxSecao(document, "05 · Acompanhamento");
            docxTabelaAcompanhamento(document, r.pontosDeAcompanhamento());

            List<AcaoAnalisadaDTO> paraDetalhar = r.analiseMenorExecucao().stream().filter(AcaoAnalisadaDTO::precisaAtencao).toList();
            docxSecao(document, "06 · Detalhamento");
            if (paraDetalhar.isEmpty()) {
                docxParagrafo(document, "Nenhuma ação desta unidade apresenta sinais objetivos de atenção (prazo apertado ou atraso frente a outros departamentos na mesma ação) — ver a análise qualitativa de cada ação em Pontos de Atenção.");
            } else {
                for (AcaoAnalisadaDTO a : paraDetalhar) {
                    docxItemAcaoAnalisadaDTO(document, a);
                }
            }

            docxSecaoMetodologia(document, r);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Uma linha genérica pras tabelas resumo de ações (Pontos de Atenção / Destaques) — código, tema e percentual, sem o resto do card completo. */
    private record LinhaTabelaAcao(String codigo, String tema, Double percentual) {
    }

    private void docxCapa(XWPFDocument document, RelatorioEstruturadoDTO r) {
        for (int i = 0; i < 4; i++) document.createParagraph();

        XWPFParagraph rotulo = document.createParagraph();
        rotulo.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun rotuloRun = rotulo.createRun();
        rotuloRun.setText("RELATÓRIO DE DESEMPENHO");
        rotuloRun.setFontSize(12);
        rotuloRun.setColor(COR_METADADO);
        rotuloRun.setCharacterSpacing(2);

        document.createParagraph();

        XWPFParagraph titulo = document.createParagraph();
        titulo.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun tituloRun = titulo.createRun();
        tituloRun.setText(r.departamento());
        tituloRun.setBold(true);
        tituloRun.setFontSize(28);
        tituloRun.setColor(COR_DESTAQUE);

        XWPFParagraph subtitulo = document.createParagraph();
        subtitulo.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtituloRun = subtitulo.createRun();
        subtituloRun.setText("Plano de Acompanhamento do Trabalho — " + r.tipo());
        subtituloRun.setFontSize(13);
        subtituloRun.setColor(COR_TEXTO_CORPO);

        XWPFParagraph data = document.createParagraph();
        data.setAlignment(ParagraphAlignment.CENTER);
        data.setSpacingBefore(200);
        XWPFRun dataRun = data.createRun();
        dataRun.setText(r.geradoEm());
        dataRun.setItalic(true);
        dataRun.setFontSize(10);
        dataRun.setColor(COR_METADADO);

        for (int i = 0; i < 10; i++) document.createParagraph();

        XWPFParagraph rodape = document.createParagraph();
        rodape.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun rodapeRun = rodape.createRun();
        rodapeRun.setText("PROAP · UFT");
        rodapeRun.setFontSize(9);
        rodapeRun.setColor(COR_METADADO);

        document.createParagraph().setPageBreak(true);
    }

    /** Extrai o valor formatado de um indicador pelo rótulo (usado pra puxar a % de execução geral pros números grandes da capa executiva). */
    private String valorIndicador(List<IndicadorRelatorioDTO> indicadores, String rotulo) {
        return indicadores.stream()
                .filter(i -> i.rotulo().equals(rotulo))
                .map(IndicadorRelatorioDTO::valor)
                .findFirst()
                .orElse("—");
    }

    private void docxNumerosGrandes(XWPFDocument document, RelatorioEstruturadoDTO r) {
        DistribuicaoExecucaoDTO d = r.distribuicao();
        String media = valorIndicador(r.indicadores(), "Média Geral de Execução");

        XWPFParagraph resumo = document.createParagraph();
        resumo.setSpacingAfter(160);
        XWPFRun resumoRun = resumo.createRun();
        resumoRun.setText(d.total() + " ações  ·  " + media);
        resumoRun.setBold(true);
        resumoRun.setFontSize(14);
        resumoRun.setColor(COR_DESTAQUE);

        XWPFTable tiles = document.createTable(1, 3);
        tiles.setWidth("100%");
        tiles.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        tiles.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        tiles.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        tiles.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        tiles.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        tiles.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");

        docxStatTile(tiles.getRow(0).getCell(0), String.valueOf(d.semExecucao()), "Sem execução registrada", COR_SEM_EXECUCAO, COR_CARD_FUNDO);
        docxStatTile(tiles.getRow(0).getCell(1), String.valueOf(d.emExecucao()), "Em execução", COR_EM_EXECUCAO, COR_CARD_FUNDO_EXECUCAO);
        docxStatTile(tiles.getRow(0).getCell(2), String.valueOf(d.concluidas()), "Concluídas", COR_POSITIVO, COR_CARD_FUNDO_POSITIVO);

        docxEspacoEntreCards(document);
    }

    private void docxStatTile(XWPFTableCell celula, String numero, String rotulo, String cor, String fundo) {
        celula.setColor(fundo);
        celula.removeParagraph(0);

        XWPFParagraph pNumero = celula.addParagraph();
        pNumero.setAlignment(ParagraphAlignment.CENTER);
        pNumero.setSpacingBefore(120);
        XWPFRun runNumero = pNumero.createRun();
        runNumero.setText(numero);
        runNumero.setBold(true);
        runNumero.setFontSize(26);
        runNumero.setColor(cor);

        XWPFParagraph pRotulo = celula.addParagraph();
        pRotulo.setAlignment(ParagraphAlignment.CENTER);
        pRotulo.setSpacingAfter(120);
        XWPFRun runRotulo = pRotulo.createRun();
        runRotulo.setText(rotulo);
        runRotulo.setFontSize(9);
        runRotulo.setColor(COR_METADADO);
    }

    /** Distribuição em linhas (não barra proporcional — o Word não é o ambiente certo pra medir largura de célula em pixels com precisão; ver EscritorPdf.barraDistribuicao no PDF pra a versão visual). */
    private void docxDistribuicao(XWPFDocument document, DistribuicaoExecucaoDTO d) {
        int total = Math.max(d.total(), 1);
        docxLinhaDistribuicao(document, "Sem execução registrada", d.semExecucao(), total, COR_SEM_EXECUCAO);
        docxLinhaDistribuicao(document, "Em execução", d.emExecucao(), total, COR_EM_EXECUCAO);
        docxLinhaDistribuicao(document, "Concluídas", d.concluidas(), total, COR_POSITIVO);
        docxEspacoEntreCards(document);
    }

    private void docxLinhaDistribuicao(XWPFDocument document, String rotulo, int quantidade, int total, String cor) {
        XWPFTable card = document.createTable(1, 1);
        docxCardSemBordas(card, cor);
        XWPFTableCell cell = card.getRow(0).getCell(0);
        cell.setColor("FAFAFB");
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(rotulo + "   " + quantidade + " (" + formatarPercentual(quantidade * 100.0 / total) + ")");
        run.setBold(true);
        run.setFontSize(10.5);
        run.setColor(COR_TEXTO_CORPO);
    }

    private void docxTabelaAcoes(XWPFDocument document, List<LinhaTabelaAcao> linhas, String corDestaque) {
        if (linhas.isEmpty()) {
            docxParagrafo(document, "Nenhum registro nesta categoria.");
            return;
        }
        XWPFTable tabela = document.createTable(linhas.size() + 1, 3);
        tabela.setWidth("100%");
        docxCabecalhoTabela(tabela, List.of("Ação", "Tema", "Execução"), corDestaque);
        for (int i = 0; i < linhas.size(); i++) {
            LinhaTabelaAcao l = linhas.get(i);
            XWPFTableRow linha = tabela.getRow(i + 1);
            docxCelulaTexto(linha.getCell(0), l.codigo(), false);
            docxCelulaTexto(linha.getCell(1), l.tema(), false);
            docxCelulaTexto(linha.getCell(2), formatarPercentual(l.percentual()), true);
        }
        docxEspacoEntreCards(document);
    }

    private void docxTabelaAcompanhamento(XWPFDocument document, List<PontoAcompanhamentoDTO> pontos) {
        if (pontos.isEmpty()) {
            docxParagrafo(document, "Nenhum ponto de acompanhamento sugerido pelos dados desta unidade.");
            return;
        }
        XWPFTable tabela = document.createTable(pontos.size() + 1, 2);
        tabela.setWidth("100%");
        docxCabecalhoTabela(tabela, List.of("Tema", "O que verificar"), COR_DESTAQUE);
        for (int i = 0; i < pontos.size(); i++) {
            PontoAcompanhamentoDTO p = pontos.get(i);
            XWPFTableRow linha = tabela.getRow(i + 1);
            docxCelulaTexto(linha.getCell(0), p.tema(), false);
            docxCelulaTexto(linha.getCell(1), p.oQueVerificar(), false);
        }
        docxEspacoEntreCards(document);
    }

    private void docxCabecalhoTabela(XWPFTable tabela, List<String> colunas, String cor) {
        XWPFTableRow cabecalho = tabela.getRow(0);
        for (int i = 0; i < colunas.size(); i++) {
            XWPFTableCell celula = cabecalho.getCell(i);
            celula.setColor(cor.equals(COR_ATENCAO) ? COR_CARD_FUNDO_ATENCAO : cor.equals(COR_POSITIVO) ? COR_CARD_FUNDO_POSITIVO : COR_CARD_FUNDO);
            celula.removeParagraph(0);
            XWPFParagraph p = celula.addParagraph();
            XWPFRun run = p.createRun();
            run.setText(colunas.get(i));
            run.setBold(true);
            run.setFontSize(10);
            run.setColor(cor);
        }
    }

    private void docxCelulaTexto(XWPFTableCell celula, String texto, boolean negrito) {
        celula.removeParagraph(0);
        XWPFParagraph p = celula.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(texto == null ? "—" : texto);
        run.setFontSize(10);
        run.setBold(negrito);
        run.setColor(COR_TEXTO_CORPO);
    }

    private void docxSecaoMetodologia(XWPFDocument document, RelatorioEstruturadoDTO r) {
        docxSecao(document, "Sobre este Relatório");
        docxParagrafo(document, "Fonte: " + r.tipo() + " (Plano Anual de Trabalho, ano corrente) · Unidade analisada: " + r.departamento() + " · Gerado em: " + r.geradoEm() + ".");

        XWPFParagraph pComo = document.createParagraph();
        pComo.setSpacingBefore(100);
        pComo.setSpacingAfter(60);
        XWPFRun runComo = pComo.createRun();
        runComo.setText("Como interpretar");
        runComo.setBold(true);
        runComo.setFontSize(11);
        runComo.setColor(COR_DESTAQUE);

        for (String linha : List.of(
                "100% de execução — ação concluída.",
                "1% a 99% — ação em execução.",
                "0% — sem execução registrada.",
                "Atrasada — prazo da tarefa já vencido, independente do percentual da ação.")) {
            XWPFParagraph p = document.createParagraph();
            p.setSpacingAfter(30);
            XWPFRun run = p.createRun();
            run.setText("• " + linha);
            run.setFontSize(10);
            run.setColor(COR_TEXTO_CORPO);
        }

        XWPFParagraph aviso = document.createParagraph();
        aviso.setSpacingBefore(100);
        XWPFRun avisoRun = aviso.createRun();
        avisoRun.setText("0% de execução não significa necessariamente atraso — o indicador representa ausência de execução registrada no período analisado, o que pode ser esperado dependendo da natureza e do momento da ação.");
        avisoRun.setItalic(true);
        avisoRun.setFontSize(9.5);
        avisoRun.setColor(COR_METADADO);
    }

    private void docxSecao(XWPFDocument document, String texto) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(180);
        p.setBorderBottom(Borders.SINGLE);
        XWPFRun run = p.createRun();
        run.setText(texto);
        run.setBold(true);
        run.setFontSize(13);
        run.setColor(COR_DESTAQUE);
    }

    private void docxParagrafo(XWPFDocument document, String texto) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingAfter(120);
        XWPFRun run = p.createRun();
        run.setText(texto);
        run.setFontSize(11);
    }

    private static final String COR_ATENCAO = "C00000";
    private static final String COR_POSITIVO = "2E7D32";
    private static final String COR_EM_EXECUCAO = "2E6F9E";
    private static final String COR_SEM_EXECUCAO = "8A8A94";
    private static final String COR_TEXTO_CORPO = "3C3C46";
    private static final String COR_METADADO = "75757F";
    private static final String COR_CARD_FUNDO = "F5F7FA";
    private static final String COR_CARD_FUNDO_ATENCAO = "FBF2F1";
    private static final String COR_CARD_FUNDO_POSITIVO = "F0F7F1";
    private static final String COR_CARD_FUNDO_EXECUCAO = "EEF3F8";

    /** Remove as bordas da tabela usada como "card" (só a esquerda fica, como faixa de destaque colorida). */
    private void docxCardSemBordas(XWPFTable card, String corAccent) {
        card.setWidth("100%");
        card.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        card.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        card.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        card.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        card.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        card.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 28, 0, corAccent);
    }

    private XWPFParagraph docxEspacoEntreCards(XWPFDocument document) {
        XWPFParagraph espaco = document.createParagraph();
        espaco.setSpacingAfter(100);
        return espaco;
    }

    /** Item completo da análise de menor execução: card (navy ou vermelho, se precisa atenção) com título, % , tarefas, comparação entre departamentos e a análise da IA. */
    private void docxItemAcaoAnalisadaDTO(XWPFDocument document, AcaoAnalisadaDTO a) {
        String accent = a.precisaAtencao() ? COR_ATENCAO : COR_DESTAQUE;
        String fundo = a.precisaAtencao() ? COR_CARD_FUNDO_ATENCAO : COR_CARD_FUNDO;

        XWPFTable card = document.createTable(1, 1);
        docxCardSemBordas(card, accent);
        XWPFTableCell cell = card.getRow(0).getCell(0);
        cell.setColor(fundo);
        cell.removeParagraph(0);

        XWPFParagraph pTitulo = cell.addParagraph();
        pTitulo.setSpacingAfter(60);
        XWPFRun runTitulo = pTitulo.createRun();
        runTitulo.setText(a.acao());
        runTitulo.setBold(true);
        runTitulo.setFontSize(11);
        runTitulo.setColor(accent);
        XWPFRun runPct = pTitulo.createRun();
        runPct.setText("   " + formatarPercentual(a.percentual()));
        runPct.setBold(true);
        runPct.setFontSize(11);
        runPct.setColor(accent);

        for (TarefaResponsavelDTO t : a.tarefas()) {
            XWPFParagraph pTarefa = cell.addParagraph();
            pTarefa.setSpacingAfter(20);
            XWPFRun runTarefa = pTarefa.createRun();
            runTarefa.setText("›  " + textoTarefa(t) + "   ·   " + t.responsavel() + "   ·   prazo " + t.prazo());
            runTarefa.setFontSize(9);
            runTarefa.setColor(COR_METADADO);
        }

        if (!a.outrosDepartamentos().isEmpty()) {
            XWPFParagraph pComp = cell.addParagraph();
            pComp.setSpacingAfter(20);
            XWPFRun runComp = pComp.createRun();
            runComp.setText("Também responsável: " + formatarComparativoDepartamentos(a.outrosDepartamentos()));
            runComp.setFontSize(9);
            runComp.setColor(COR_METADADO);
        }

        // A justificativa é o principal valor analítico do item — antes vinha em itálico cinza
        // (a mesma cor/peso das tarefas, um metadado secundário), o que a deixava com aparência
        // de nota de rodapé. Agora tem peso normal, cor mais escura e um rótulo próprio.
        XWPFParagraph pJust = cell.addParagraph();
        pJust.setSpacingBefore(60);
        XWPFRun runLabel = pJust.createRun();
        runLabel.setText("ANÁLISE   ");
        runLabel.setBold(true);
        runLabel.setFontSize(8);
        runLabel.setColor(accent);
        XWPFRun runJust = pJust.createRun();
        runJust.setText(a.justificativa());
        runJust.setFontSize(10);
        runJust.setColor(COR_TEXTO_CORPO);

        docxEspacoEntreCards(document);
    }

    private byte[] gerarPdf(RelatorioEstruturadoDTO r) throws Exception {
        try (PDDocument document = new PDDocument()) {
            EscritorPdf escritor = new EscritorPdf(document);

            escritor.capa(r);

            escritor.secao("01 · Visão Executiva");
            escritor.numerosGrandes(r);
            escritor.barraDistribuicao(r.distribuicao());

            escritor.secao("02 · Pontos de Atenção");
            escritor.tabelaAcoes(r.analiseMenorExecucao().stream()
                    .map(a -> new LinhaTabelaAcao(codigoDaAcao(a.acao()), a.tema(), a.percentual()))
                    .toList(), Color.decode("#" + COR_ATENCAO));

            escritor.secao("03 · Desempenhos de Destaque");
            escritor.tabelaAcoes(r.destaquesPositivos().stream()
                    .limit(LIMITE_DESTAQUES_EXIBIDOS)
                    .map(a -> new LinhaTabelaAcao(codigoDaAcao(a.acao()), temaSemCodigo(a.acao(), codigoDaAcao(a.acao())), a.percentual()))
                    .toList(), Color.decode("#" + COR_POSITIVO));
            if (r.destaquesPositivos().size() > LIMITE_DESTAQUES_EXIBIDOS) {
                escritor.paragrafo("+ " + (r.destaquesPositivos().size() - LIMITE_DESTAQUES_EXIBIDOS) + " outras ações com bom desempenho nesta unidade.");
            }

            escritor.secao("04 · Leitura do Cenário");
            escritor.paragrafo(normalizarTextoPdf(r.resumoExecutivo()));
            for (String insight : r.leituraCenario()) {
                escritor.paragrafo("• " + normalizarTextoPdf(insight));
            }

            escritor.secao("05 · Acompanhamento");
            escritor.tabelaAcompanhamento(r.pontosDeAcompanhamento());

            List<AcaoAnalisadaDTO> paraDetalhar = r.analiseMenorExecucao().stream().filter(AcaoAnalisadaDTO::precisaAtencao).toList();
            escritor.secao("06 · Detalhamento");
            if (paraDetalhar.isEmpty()) {
                escritor.paragrafo("Nenhuma ação desta unidade apresenta sinais objetivos de atenção (prazo apertado ou atraso frente a outros departamentos na mesma ação) — ver a análise qualitativa de cada ação em Pontos de Atenção.");
            } else {
                for (AcaoAnalisadaDTO a : paraDetalhar) {
                    escritor.itemAcaoAnalisadaDTO(a);
                }
            }

            escritor.metodologia(r);

            return escritor.finalizar();
        }
    }

    private String normalizarTextoPdf(String texto) {
        return texto == null ? "" : texto.replaceAll("[^\\r\\n\\t\\x20-\\xFF]", "-");
    }

    private List<String> quebrarTextoPdf(String texto, int limite) {
        java.util.ArrayList<String> linhas = new java.util.ArrayList<>();
        StringBuilder atual = new StringBuilder();

        for (String palavra : texto.split("\\s+")) {
            if (atual.length() + palavra.length() + 1 > limite && atual.length() > 0) {
                linhas.add(atual.toString());
                atual.setLength(0);
            }
            if (atual.length() > 0) atual.append(' ');
            atual.append(palavra);
        }

        if (atual.length() > 0) linhas.add(atual.toString());
        return linhas.isEmpty() ? List.of("") : linhas;
    }

    /**
     * PDFBox não tem título/tabela prontos como o POI (DOCX) — esse helper concentra
     * a posição atual (y), a paginação automática e o desenho de cada elemento, pra
     * gerarPdf() ficar declarativo (uma chamada por seção) em vez de manipular
     * coordenada e quebra de página na mão em todo lugar.
     */
    private class EscritorPdf {
        private final float margem = 56;
        private final float larguraUtil = PDRectangle.A4.getWidth() - 2 * margem;

        private final PDDocument document;
        private PDPageContentStream content;
        private float y;

        EscritorPdf(PDDocument document) throws Exception {
            this.document = document;
            novaPagina();
        }

        private void novaPagina() throws Exception {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - margem;
        }

        private void garantirEspaco(float necessario) throws Exception {
            if (y - necessario < margem) {
                novaPagina();
            }
        }

        void titulo(String texto) throws Exception {
            content.setNonStrokingColor(31, 78, 121);
            for (String trecho : quebrarTextoPdf(texto, 70)) {
                garantirEspaco(20);
                escreverLinha(trecho, PDType1Font.HELVETICA_BOLD, 15, margem);
            }
            content.setNonStrokingColor(0, 0, 0);
            y -= 4;
        }

        void subtitulo(String texto) throws Exception {
            content.setNonStrokingColor(90, 90, 90);
            for (String trecho : quebrarTextoPdf(texto, 95)) {
                garantirEspaco(20);
                escreverLinha(trecho, PDType1Font.HELVETICA_OBLIQUE, 10, margem);
            }
            content.setNonStrokingColor(0, 0, 0);
            y -= 12;
        }

        void secao(String texto) throws Exception {
            garantirEspaco(30);
            y -= 10;
            content.setNonStrokingColor(31, 78, 121);
            escreverLinha(texto, PDType1Font.HELVETICA_BOLD, 12, margem);
            content.setStrokingColor(31, 78, 121);
            content.moveTo(margem, y + 2);
            content.lineTo(margem + larguraUtil, y + 2);
            content.stroke();
            content.setNonStrokingColor(0, 0, 0);
            content.setStrokingColor(0, 0, 0);
            y -= 18;
        }

        void paragrafo(String texto) throws Exception {
            for (String trecho : quebrarTextoPdf(texto, 95)) {
                garantirEspaco(14);
                escreverLinha(trecho, PDType1Font.HELVETICA, 10, margem);
            }
            y -= 6;
        }

        private static final float ALTURA_PADDING_CARD = 8;

        /** Item completo: card (navy ou vermelho, se precisa atenção) com título+%, tarefas, comparação entre departamentos e a análise da IA. */
        void itemAcaoAnalisadaDTO(AcaoAnalisadaDTO a) throws Exception {
            Color accent = Color.decode("#" + (a.precisaAtencao() ? COR_ATENCAO : COR_DESTAQUE));
            Color fundo = Color.decode("#" + (a.precisaAtencao() ? COR_CARD_FUNDO_ATENCAO : COR_CARD_FUNDO));
            Color corCorpo = Color.decode("#" + COR_TEXTO_CORPO);
            Color corMetadado = Color.decode("#" + COR_METADADO);
            float xTexto = margem + 14;

            List<String> linhasTitulo = quebrarTextoPdf(
                    normalizarTextoPdf(a.acao()) + "   " + formatarPercentual(a.percentual()), 80);

            List<List<String>> linhasTarefas = new java.util.ArrayList<>();
            for (TarefaResponsavelDTO t : a.tarefas()) {
                String linha = "- " + textoTarefa(t) + " | " + t.responsavel() + " | prazo " + t.prazo();
                linhasTarefas.add(quebrarTextoPdf(normalizarTextoPdf(linha), 88));
            }

            List<String> linhasComparativo = a.outrosDepartamentos().isEmpty() ? List.of()
                    : quebrarTextoPdf(normalizarTextoPdf("Também responsável: " + formatarComparativoDepartamentos(a.outrosDepartamentos())), 88);

            List<String> linhasJustificativa = quebrarTextoPdf(normalizarTextoPdf(a.justificativa()), 88);

            // Pré-calcula a altura total do card ANTES de desenhar (soma de todas as linhas já
            // quebradas, cada uma "custando" tamanho+4 de altura, igual ao que escreverLinha
            // consome de verdade) — só assim dá pra desenhar o retângulo de fundo, que precisa
            // vir ANTES do texto no content stream (senão cobre o que já foi escrito).
            float altura = ALTURA_PADDING_CARD * 2;
            altura += linhasTitulo.size() * (10.5f + 4);
            for (List<String> lt : linhasTarefas) altura += lt.size() * (8 + 4);
            altura += linhasComparativo.size() * (8 + 4);
            altura += (7.5f + 4);
            altura += linhasJustificativa.size() * (9.5f + 4);

            garantirEspaco(altura);
            desenharFundoCard(fundo, accent, altura);
            y -= ALTURA_PADDING_CARD;

            for (String trecho : linhasTitulo) {
                escreverLinhaEm(trecho, PDType1Font.HELVETICA_BOLD, 10.5f, xTexto, accent);
            }
            for (List<String> lt : linhasTarefas) {
                for (String trecho : lt) {
                    escreverLinhaEm(trecho, PDType1Font.HELVETICA, 8, xTexto, corMetadado);
                }
            }
            for (String trecho : linhasComparativo) {
                escreverLinhaEm(trecho, PDType1Font.HELVETICA, 8, xTexto, corMetadado);
            }
            // A justificativa é o principal valor analítico do item — antes saía em itálico e na
            // mesma cor cinza-clara das tarefas (metadado secundário), o que a deixava com cara
            // de nota de rodapé. Agora tem rótulo próprio, peso normal e cor mais escura.
            escreverLinhaEm("ANÁLISE", PDType1Font.HELVETICA_BOLD, 7.5f, xTexto, accent);
            for (String trecho : linhasJustificativa) {
                escreverLinhaEm(trecho, PDType1Font.HELVETICA, 9.5f, xTexto, corCorpo);
            }

            y -= ALTURA_PADDING_CARD + 10;
        }

        /** Fundo colorido + faixa de destaque à esquerda, ocupando a largura útil inteira a partir de y (topo) até y-altura. */
        private void desenharFundoCard(Color fundo, Color accent, float altura) throws Exception {
            float topo = y;
            content.setNonStrokingColor(fundo.getRed(), fundo.getGreen(), fundo.getBlue());
            content.addRect(margem, topo - altura, larguraUtil, altura);
            content.fill();
            content.setNonStrokingColor(accent.getRed(), accent.getGreen(), accent.getBlue());
            content.addRect(margem, topo - altura, 3, altura);
            content.fill();
            content.setNonStrokingColor(0, 0, 0);
        }

        /** Desenha texto numa posição (x, yPos) EXPLÍCITA, sem tocar no "y" corrente do escritor — usado onde vários textos precisam ficar lado a lado na mesma linha (capa, cabeçalho de tabela, colunas). */
        private void escreverXY(String texto, PDType1Font fonte, float tamanho, float x, float yPos, Color cor) throws Exception {
            content.setNonStrokingColor(cor.getRed(), cor.getGreen(), cor.getBlue());
            content.beginText();
            content.setFont(fonte, tamanho);
            content.newLineAtOffset(x, yPos);
            content.showText(texto);
            content.endText();
            content.setNonStrokingColor(0, 0, 0);
        }

        private float larguraTexto(String texto, PDType1Font fonte, float tamanho) throws Exception {
            return fonte.getStringWidth(texto) / 1000 * tamanho;
        }

        private void escreverCentralizado(String texto, PDType1Font fonte, float tamanho, float yPos, Color cor) throws Exception {
            float largura = larguraTexto(texto, fonte, tamanho);
            escreverXY(texto, fonte, tamanho, margem + (larguraUtil - largura) / 2, yPos, cor);
        }

        void capa(RelatorioEstruturadoDTO r) throws Exception {
            Color corDestaque = Color.decode("#" + COR_DESTAQUE);
            Color corTexto = Color.decode("#" + COR_TEXTO_CORPO);
            Color corMetadado = Color.decode("#" + COR_METADADO);
            float altura = PDRectangle.A4.getHeight();

            float yAtual = altura - 220;
            escreverCentralizado("RELATÓRIO DE DESEMPENHO", PDType1Font.HELVETICA, 11, yAtual, corMetadado);
            yAtual -= 34;
            for (String trecho : quebrarTextoPdf(normalizarTextoPdf(r.departamento()), 28)) {
                escreverCentralizado(trecho, PDType1Font.HELVETICA_BOLD, 24, yAtual, corDestaque);
                yAtual -= 30;
            }
            yAtual -= 6;
            escreverCentralizado("Plano de Acompanhamento do Trabalho — " + normalizarTextoPdf(r.tipo()), PDType1Font.HELVETICA, 12, yAtual, corTexto);
            yAtual -= 36;
            escreverCentralizado(r.geradoEm(), PDType1Font.HELVETICA_OBLIQUE, 9, yAtual, corMetadado);

            escreverCentralizado("PROAP · UFT", PDType1Font.HELVETICA, 9, margem + 24, corMetadado);

            novaPagina();
        }

        void numerosGrandes(RelatorioEstruturadoDTO r) throws Exception {
            DistribuicaoExecucaoDTO d = r.distribuicao();
            String media = valorIndicador(r.indicadores(), "Média Geral de Execução");

            Color corResumo = Color.decode("#" + COR_DESTAQUE);
            for (String trecho : quebrarTextoPdf(d.total() + " ações  ·  " + media, 78)) {
                garantirEspaco(18);
                escreverLinhaEm(trecho, PDType1Font.HELVETICA_BOLD, 13, margem, corResumo);
            }
            y -= 4;

            float larguraTile = (larguraUtil - 16) / 3;
            float alturaTile = 62;
            garantirEspaco(alturaTile + 12);
            float topo = y;
            desenharStatTile(margem, topo, larguraTile, alturaTile, String.valueOf(d.semExecucao()), "Sem execução registrada",
                    Color.decode("#" + COR_SEM_EXECUCAO), Color.decode("#" + COR_CARD_FUNDO));
            desenharStatTile(margem + larguraTile + 8, topo, larguraTile, alturaTile, String.valueOf(d.emExecucao()), "Em execução",
                    Color.decode("#" + COR_EM_EXECUCAO), Color.decode("#" + COR_CARD_FUNDO_EXECUCAO));
            desenharStatTile(margem + 2 * (larguraTile + 8), topo, larguraTile, alturaTile, String.valueOf(d.concluidas()), "Concluídas",
                    Color.decode("#" + COR_POSITIVO), Color.decode("#" + COR_CARD_FUNDO_POSITIVO));
            y = topo - alturaTile - 16;
        }

        private void desenharStatTile(float x, float topo, float largura, float altura, String numero, String rotulo, Color cor, Color fundo) throws Exception {
            content.setNonStrokingColor(fundo.getRed(), fundo.getGreen(), fundo.getBlue());
            content.addRect(x, topo - altura, largura, altura);
            content.fill();
            content.setNonStrokingColor(0, 0, 0);

            float larguraNumero = larguraTexto(numero, PDType1Font.HELVETICA_BOLD, 22);
            escreverXY(numero, PDType1Font.HELVETICA_BOLD, 22, x + (largura - larguraNumero) / 2, topo - 32, cor);

            float larguraRotulo = larguraTexto(rotulo, PDType1Font.HELVETICA, 8.5f);
            escreverXY(rotulo, PDType1Font.HELVETICA, 8.5f, x + (largura - larguraRotulo) / 2, topo - 48, Color.decode("#" + COR_METADADO));
        }

        /** Barra horizontal proporcional (sem/em/concluídas) — no Word isso vira linhas empilhadas (ver docxDistribuicao), mas aqui dá pra controlar posição em pontos com precisão. */
        void barraDistribuicao(DistribuicaoExecucaoDTO d) throws Exception {
            int total = Math.max(d.total(), 1);
            float altura = 26;
            garantirEspaco(altura + 26);
            float topo = y;
            float x = margem;
            int[] valores = {d.semExecucao(), d.emExecucao(), d.concluidas()};
            String[] cores = {COR_SEM_EXECUCAO, COR_EM_EXECUCAO, COR_POSITIVO};
            String[] rotulos = {"Sem execução registrada", "Em execução", "Concluídas"};

            for (int i = 0; i < 3; i++) {
                float largura = larguraUtil * valores[i] / (float) total;
                if (largura < 0.5f) continue;
                Color cor = Color.decode("#" + cores[i]);
                content.setNonStrokingColor(cor.getRed(), cor.getGreen(), cor.getBlue());
                content.addRect(x, topo - altura, largura, altura);
                content.fill();
                content.setNonStrokingColor(0, 0, 0);
                x += largura;
            }
            y = topo - altura - 14;

            float xLegenda = margem;
            for (int i = 0; i < 3; i++) {
                Color cor = Color.decode("#" + cores[i]);
                content.setNonStrokingColor(cor.getRed(), cor.getGreen(), cor.getBlue());
                content.addRect(xLegenda, y - 7, 8, 8);
                content.fill();
                content.setNonStrokingColor(0, 0, 0);
                String texto = rotulos[i] + " " + formatarPercentual(valores[i] * 100.0 / total);
                escreverXY(texto, PDType1Font.HELVETICA, 8.5f, xLegenda + 12, y - 6, Color.decode("#" + COR_TEXTO_CORPO));
                xLegenda += 12 + larguraTexto(texto, PDType1Font.HELVETICA, 8.5f) + 18;
            }
            y -= 22;
        }

        /** Tabela Ação | Tema | Execução — usada tanto pra Pontos de Atenção quanto Desempenhos de Destaque, só muda a cor do cabeçalho. */
        void tabelaAcoes(List<LinhaTabelaAcao> linhas, Color corCabecalho) throws Exception {
            if (linhas.isEmpty()) {
                paragrafo("Nenhum registro nesta categoria.");
                return;
            }
            float colAcao = 72, colExecucao = 55;
            float xAcao = margem + 6;
            float xTema = margem + colAcao + 4;
            float xExecucaoFim = margem + larguraUtil - 6;

            garantirEspaco(22);
            float topoCab = y;
            content.setNonStrokingColor(corCabecalho.getRed(), corCabecalho.getGreen(), corCabecalho.getBlue());
            content.addRect(margem, topoCab - 20, larguraUtil, 20);
            content.fill();
            content.setNonStrokingColor(0, 0, 0);
            escreverXY("Ação", PDType1Font.HELVETICA_BOLD, 9, xAcao, topoCab - 14, Color.WHITE);
            escreverXY("Tema", PDType1Font.HELVETICA_BOLD, 9, xTema, topoCab - 14, Color.WHITE);
            float larguraRotuloExec = larguraTexto("Execução", PDType1Font.HELVETICA_BOLD, 9);
            escreverXY("Execução", PDType1Font.HELVETICA_BOLD, 9, xExecucaoFim - larguraRotuloExec, topoCab - 14, Color.WHITE);
            y = topoCab - 22;

            Color corTexto = Color.decode("#" + COR_TEXTO_CORPO);
            boolean linhaClara = true;
            for (LinhaTabelaAcao l : linhas) {
                List<String> linhasTema = quebrarTextoPdf(normalizarTextoPdf(l.tema()), 58);
                float alturaLinha = Math.max(1, linhasTema.size()) * 12 + 6;
                garantirEspaco(alturaLinha);
                float topo = y;
                if (linhaClara) {
                    content.setNonStrokingColor(249, 249, 250);
                    content.addRect(margem, topo - alturaLinha, larguraUtil, alturaLinha);
                    content.fill();
                    content.setNonStrokingColor(0, 0, 0);
                }
                escreverXY(l.codigo(), PDType1Font.HELVETICA_BOLD, 9, xAcao, topo - 12, corTexto);
                float yTema = topo - 12;
                for (String trecho : linhasTema) {
                    escreverXY(trecho, PDType1Font.HELVETICA, 9, xTema, yTema, corTexto);
                    yTema -= 12;
                }
                String pct = formatarPercentual(l.percentual());
                float larguraPct = larguraTexto(pct, PDType1Font.HELVETICA_BOLD, 9);
                escreverXY(pct, PDType1Font.HELVETICA_BOLD, 9, xExecucaoFim - larguraPct, topo - 12, corCabecalho);
                y = topo - alturaLinha;
                linhaClara = !linhaClara;
            }
            y -= 10;
        }

        /** Tabela Tema | O que verificar — pontos de acompanhamento sugeridos pelo sistema. */
        void tabelaAcompanhamento(List<PontoAcompanhamentoDTO> pontos) throws Exception {
            if (pontos.isEmpty()) {
                paragrafo("Nenhum ponto de acompanhamento sugerido pelos dados desta unidade.");
                return;
            }
            float colTema = 130;
            float xTema = margem + 6;
            float xVerificar = margem + colTema + 4;
            Color corCab = Color.decode("#" + COR_DESTAQUE);

            garantirEspaco(22);
            float topoCab = y;
            content.setNonStrokingColor(corCab.getRed(), corCab.getGreen(), corCab.getBlue());
            content.addRect(margem, topoCab - 20, larguraUtil, 20);
            content.fill();
            content.setNonStrokingColor(0, 0, 0);
            escreverXY("Tema", PDType1Font.HELVETICA_BOLD, 9, xTema, topoCab - 14, Color.WHITE);
            escreverXY("O que verificar", PDType1Font.HELVETICA_BOLD, 9, xVerificar, topoCab - 14, Color.WHITE);
            y = topoCab - 22;

            Color corTexto = Color.decode("#" + COR_TEXTO_CORPO);
            boolean linhaClara = true;
            for (PontoAcompanhamentoDTO p : pontos) {
                List<String> linhasTema = quebrarTextoPdf(normalizarTextoPdf(p.tema()), 22);
                List<String> linhasVerificar = quebrarTextoPdf(normalizarTextoPdf(p.oQueVerificar()), 68);
                int maxLinhas = Math.max(linhasTema.size(), linhasVerificar.size());
                float alturaLinha = maxLinhas * 12 + 6;
                garantirEspaco(alturaLinha);
                float topo = y;
                if (linhaClara) {
                    content.setNonStrokingColor(249, 249, 250);
                    content.addRect(margem, topo - alturaLinha, larguraUtil, alturaLinha);
                    content.fill();
                    content.setNonStrokingColor(0, 0, 0);
                }
                float yTema = topo - 12;
                for (String trecho : linhasTema) {
                    escreverXY(trecho, PDType1Font.HELVETICA_BOLD, 9, xTema, yTema, corTexto);
                    yTema -= 12;
                }
                float yVer = topo - 12;
                for (String trecho : linhasVerificar) {
                    escreverXY(trecho, PDType1Font.HELVETICA, 9, xVerificar, yVer, corTexto);
                    yVer -= 12;
                }
                y = topo - alturaLinha;
                linhaClara = !linhaClara;
            }
            y -= 10;
        }

        void metodologia(RelatorioEstruturadoDTO r) throws Exception {
            secao("Sobre este Relatório");
            paragrafo(normalizarTextoPdf("Fonte: " + r.tipo() + " (Plano Anual de Trabalho, ano corrente) · Unidade analisada: " + r.departamento() + " · Gerado em: " + r.geradoEm() + "."));

            garantirEspaco(16);
            escreverLinhaEm("Como interpretar", PDType1Font.HELVETICA_BOLD, 11, margem, Color.decode("#" + COR_DESTAQUE));
            for (String linha : List.of(
                    "100% de execução — ação concluída.",
                    "1% a 99% — ação em execução.",
                    "0% — sem execução registrada.",
                    "Atrasada — prazo da tarefa já vencido, independente do percentual da ação.")) {
                garantirEspaco(13);
                escreverLinhaEm("• " + linha, PDType1Font.HELVETICA, 9.5f, margem, Color.decode("#" + COR_TEXTO_CORPO));
            }
            y -= 6;
            for (String trecho : quebrarTextoPdf(normalizarTextoPdf(
                    "0% de execução não significa necessariamente atraso: o indicador representa ausência de execução registrada no período analisado, o que pode ser esperado dependendo da natureza e do momento da ação."), 95)) {
                garantirEspaco(13);
                escreverLinhaEm(trecho, PDType1Font.HELVETICA_OBLIQUE, 9, margem, Color.decode("#" + COR_METADADO));
            }
        }

        private void escreverLinha(String texto, PDType1Font fonte, float tamanho, float x) throws Exception {
            content.beginText();
            content.setFont(fonte, tamanho);
            content.newLineAtOffset(x, y);
            content.showText(texto);
            content.endText();
            y -= tamanho + 4;
        }

        private void escreverLinhaEm(String texto, PDType1Font fonte, float tamanho, float x, Color cor) throws Exception {
            content.setNonStrokingColor(cor.getRed(), cor.getGreen(), cor.getBlue());
            escreverLinha(texto, fonte, tamanho, x);
            content.setNonStrokingColor(0, 0, 0);
        }

        byte[] finalizar() throws Exception {
            content.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private String enviarParaBucket(byte[] arquivoBytes, String departamento, Long relatorioId, String extensao, String contentType) throws Exception {
        String nomeSeguro = "relatorio_" + departamento.replaceAll("[^a-zA-Z0-9]", "_") + "_" + relatorioId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extensao;
        String urlUpload = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeSeguro;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlUpload))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(arquivoBytes))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return gerarUrlAssinada(nomeSeguro);
        } else {
            throw new RuntimeException("Falha ao enviar relatório para o bucket: " + response.body());
        }
    }

    /**
     * Gera uma URL assinada (temporária) para o arquivo, funcionando mesmo se o bucket
     * "proiap-documentos" não estiver configurado como público no Supabase.
     * Válida por 7 dias (604800 segundos).
     */
    private String gerarUrlAssinada(String nomeArquivo) throws Exception {
        String urlSign = supabaseUrl + "/storage/v1/object/sign/proiap-documentos/" + nomeArquivo;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlSign))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expiresIn\": 604800}"))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
            String signedPath = (String) json.get("signedURL");
            if (signedPath == null) {
                throw new RuntimeException("Resposta do Supabase sem signedURL: " + response.body());
            }
            return supabaseUrl + "/storage/v1" + signedPath;
        } else {
            throw new RuntimeException("Falha ao gerar URL assinada: " + response.body());
        }
    }

    private String extrairNomeArquivoDoBucket(String arquivoUrl) {
        if (arquivoUrl == null || arquivoUrl.isBlank()) {
            return null;
        }

        String[] marcadores = {
            "/storage/v1/object/sign/proiap-documentos/",
            "/storage/v1/object/proiap-documentos/"
        };

        for (String marcador : marcadores) {
            int inicio = arquivoUrl.indexOf(marcador);
            if (inicio < 0) continue;

            String nome = arquivoUrl.substring(inicio + marcador.length());
            int queryIndex = nome.indexOf('?');
            if (queryIndex >= 0) {
                nome = nome.substring(0, queryIndex);
            }
            return URLDecoder.decode(nome, StandardCharsets.UTF_8);
        }

        return null;
    }

    private void excluirArquivoDoBucket(String nomeArquivo) throws Exception {
        String urlDelete = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeArquivo;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlDelete))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("apikey", supabaseServiceRoleKey)
                .DELETE()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(response.body());
        }
    }
}
