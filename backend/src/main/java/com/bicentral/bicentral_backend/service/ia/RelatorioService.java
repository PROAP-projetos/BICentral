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
import com.bicentral.bicentral_backend.dto.relatorio.IndicadorRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.JustificativaAcaoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioConteudoIADTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.TarefaResponsavelDTO;

import jakarta.annotation.PostConstruct;
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
                    dados.indicadores(),
                    combinarAnaliseComJustificativas(departamento, dados.piores(), conteudo.analiseMenorExecucao()),
                    dados.melhores(),
                    conteudo.recomendacoes());

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
        List<AcaoAnalisadaDTO> resultado = new ArrayList<>();
        int total = Math.min(piores.size(), justificativas.size());
        for (int i = 0; i < total; i++) {
            AcaoComCodigo acao = piores.get(i);
            JustificativaAcaoDTO justificativa = justificativas.get(i);
            List<TarefaResponsavelDTO> tarefas = buscarTarefasDaAcao(departamento, acao.codigo());
            List<DepartamentoParceiroDTO> outrosDepartamentos = buscarOutrosDepartamentos(acao.codigo(), departamento);
            resultado.add(new AcaoAnalisadaDTO(
                    acao.acao(),
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
     * Tarefas operacionais do próprio departamento sob essa ação (pat_tarefas),
     * com responsável e prazo — dá contexto de "quem" e "quando" à justificativa da IA.
     */
    private List<TarefaResponsavelDTO> buscarTarefasDaAcao(String departamento, String codigoAcao) {
        if (codigoAcao == null || codigoAcao.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> tarefas = jdbcTemplate.queryForList("""
                SELECT
                    dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    dados_completos->>'Responsável' AS responsavel,
                    to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final
                FROM pat_tarefas
                WHERE departamento ILIKE ?
                    AND substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') = ?
                ORDER BY to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') ASC NULLS LAST
                LIMIT 3
                """, "%" + departamento.trim() + "%", codigoAcao);

            List<TarefaResponsavelDTO> resultado = new ArrayList<>();
            for (Map<String, Object> t : tarefas) {
                resultado.add(new TarefaResponsavelDTO(
                        (String) t.get("titulo_tarefa"),
                        (String) t.get("responsavel"),
                        formatarPrazo((java.sql.Date) t.get("data_final"))));
            }
            return resultado;
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar tarefas da ação '" + codigoAcao + "': " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Outros departamentos que também respondem por essa mesma ação, com o percentual
     * de execução de cada um (pat_execucao_departamento) — visibilidade cruzada sem a
     * IA precisar (ou poder) apontar responsabilidade de outro setor.
     */
    private List<DepartamentoParceiroDTO> buscarOutrosDepartamentos(String codigoAcao, String departamentoAtual) {
        if (codigoAcao == null || codigoAcao.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                SELECT departamento, ROUND(percentual_execucao * 100, 2) AS percentual
                FROM pat_execucao_departamento
                WHERE codigo_acao = ? AND departamento NOT ILIKE ?
                ORDER BY percentual_execucao DESC
                """, codigoAcao, "%" + departamentoAtual.trim() + "%");

            return linhas.stream()
                    .map(row -> new DepartamentoParceiroDTO((String) row.get("departamento"), toDouble(row.get("percentual"))))
                    .toList();
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar departamentos parceiros da ação '" + codigoAcao + "': " + e.getMessage());
            return List.of();
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
            List<AcaoComCodigo> piores,
            List<AcaoRelatorioDTO> melhores) {
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
                            "[" + row.get("codigo") + "] " + row.get("titulo"),
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
            return new DadosQuantitativosRelatorio(indicadores, piores, melhores);
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
        indicadores.add(new IndicadorRelatorioDTO("Média Geral de Execução", formatarPercentual(toDouble(resumo.get("media_geral")))));
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

        return new DadosQuantitativosRelatorio(
                indicadores,
                piores.stream().map(row -> new AcaoComCodigo(
                        (String) row.get("codigo_acao"),
                        (String) row.get("titulo_acao"),
                        toDouble(row.get("percentual")))).toList(),
                melhores.stream().map(row -> new AcaoRelatorioDTO((String) row.get("titulo_acao"), toDouble(row.get("percentual")))).toList());
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

    private static final String COR_DESTAQUE = "1F4E79";

    private byte[] gerarDocx(RelatorioEstruturadoDTO r) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {

            XWPFParagraph titulo = document.createParagraph();
            titulo.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tituloRun = titulo.createRun();
            tituloRun.setText("Relatório de Desempenho - " + r.departamento());
            tituloRun.setBold(true);
            tituloRun.setFontSize(16);
            tituloRun.setColor(COR_DESTAQUE);

            XWPFParagraph subtitulo = document.createParagraph();
            subtitulo.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subtituloRun = subtitulo.createRun();
            subtituloRun.setText("Plano: " + r.tipo() + " | Gerado em: " + r.geradoEm());
            subtituloRun.setItalic(true);
            subtituloRun.setFontSize(10);

            docxSecao(document, "Resumo Executivo");
            docxParagrafo(document, r.resumoExecutivo());

            docxSecao(document, "Indicadores Gerais");
            docxTabelaIndicadores(document, r.indicadores());

            docxSecao(document, "Análise das Ações com Menor Execução");
            for (AcaoAnalisadaDTO a : r.analiseMenorExecucao()) {
                docxItemAcaoAnalisadaDTO(document, a);
            }

            docxSecao(document, "Destaques Positivos");
            for (AcaoRelatorioDTO a : r.destaquesPositivos()) {
                docxItemAcao(document, a.acao(), a.percentual());
            }

            docxSecao(document, "Considerações Finais e Recomendações");
            for (String recomendacao : r.recomendacoes()) {
                XWPFParagraph p = document.createParagraph();
                p.setSpacingAfter(80);
                XWPFRun run = p.createRun();
                run.setText("• " + recomendacao);
                run.setFontSize(11);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private void docxSecao(XWPFDocument document, String texto) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(100);
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

    /** Item simples, usado só nos Destaques Positivos (sem justificativa/tarefas/comparação). */
    private void docxItemAcao(XWPFDocument document, String acao, Double percentual) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingAfter(40);
        XWPFRun run = p.createRun();
        run.setText(acao + " — " + formatarPercentual(percentual));
        run.setBold(true);
        run.setFontSize(11);
    }

    /** Item completo da análise de menor execução: título, tarefas, comparação entre departamentos e justificativa. */
    private void docxItemAcaoAnalisadaDTO(XWPFDocument document, AcaoAnalisadaDTO a) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingAfter(20);
        XWPFRun run = p.createRun();
        run.setText((a.precisaAtencao() ? "⚠ " : "") + a.acao() + " — " + formatarPercentual(a.percentual()));
        run.setBold(true);
        run.setFontSize(11);
        if (a.precisaAtencao()) {
            run.setColor(COR_ATENCAO);
        }

        for (TarefaResponsavelDTO t : a.tarefas()) {
            XWPFParagraph pTarefa = document.createParagraph();
            pTarefa.setSpacingAfter(20);
            pTarefa.setIndentationLeft(300);
            XWPFRun runTarefa = pTarefa.createRun();
            runTarefa.setText("• " + t.titulo() + " — " + t.responsavel() + " — prazo " + t.prazo());
            runTarefa.setFontSize(9);
            runTarefa.setColor("555555");
        }

        if (!a.outrosDepartamentos().isEmpty()) {
            XWPFParagraph pComp = document.createParagraph();
            pComp.setSpacingAfter(20);
            pComp.setIndentationLeft(300);
            XWPFRun runComp = pComp.createRun();
            runComp.setText("Também responsável: " + formatarComparativoDepartamentos(a.outrosDepartamentos()));
            runComp.setItalic(true);
            runComp.setFontSize(9);
            runComp.setColor("555555");
        }

        XWPFParagraph pJust = document.createParagraph();
        pJust.setSpacingAfter(140);
        pJust.setIndentationLeft(300);
        XWPFRun runJust = pJust.createRun();
        runJust.setText(a.justificativa());
        runJust.setItalic(true);
        runJust.setFontSize(10);
    }

    private void docxTabelaIndicadores(XWPFDocument document, List<IndicadorRelatorioDTO> indicadores) {
        XWPFTable tabela = document.createTable(indicadores.size(), 2);
        tabela.setWidth("100%");

        for (int i = 0; i < indicadores.size(); i++) {
            IndicadorRelatorioDTO indicador = indicadores.get(i);
            XWPFTableRow linha = tabela.getRow(i);

            XWPFTableCell celulaRotulo = linha.getCell(0);
            celulaRotulo.removeParagraph(0);
            XWPFParagraph pRotulo = celulaRotulo.addParagraph();
            XWPFRun runRotulo = pRotulo.createRun();
            runRotulo.setText(indicador.rotulo());
            runRotulo.setBold(true);
            runRotulo.setFontSize(10);

            XWPFTableCell celulaValor = linha.getCell(1);
            celulaValor.removeParagraph(0);
            XWPFParagraph pValor = celulaValor.addParagraph();
            XWPFRun runValor = pValor.createRun();
            runValor.setText(indicador.valor());
            runValor.setFontSize(10);
        }
    }

    private byte[] gerarPdf(RelatorioEstruturadoDTO r) throws Exception {
        try (PDDocument document = new PDDocument()) {
            EscritorPdf escritor = new EscritorPdf(document);

            escritor.titulo("Relatório de Desempenho - " + normalizarTextoPdf(r.departamento()));
            escritor.subtitulo("Plano: " + normalizarTextoPdf(r.tipo()) + " | Gerado em: " + r.geradoEm());

            escritor.secao("Resumo Executivo");
            escritor.paragrafo(normalizarTextoPdf(r.resumoExecutivo()));

            escritor.secao("Indicadores Gerais");
            escritor.tabelaIndicadores(r.indicadores());

            escritor.secao("Análise das Ações com Menor Execução");
            for (AcaoAnalisadaDTO a : r.analiseMenorExecucao()) {
                escritor.itemAcaoAnalisadaDTO(a);
            }

            escritor.secao("Destaques Positivos");
            for (AcaoRelatorioDTO a : r.destaquesPositivos()) {
                escritor.itemAcao(normalizarTextoPdf(a.acao()), a.percentual());
            }

            escritor.secao("Considerações Finais e Recomendações");
            for (String recomendacao : r.recomendacoes()) {
                escritor.paragrafo("- " + normalizarTextoPdf(recomendacao));
            }

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
            y -= 8;
        }

        void paragrafo(String texto) throws Exception {
            for (String trecho : quebrarTextoPdf(texto, 95)) {
                garantirEspaco(14);
                escreverLinha(trecho, PDType1Font.HELVETICA, 10, margem);
            }
            y -= 6;
        }

        /** Item simples, usado só nos Destaques Positivos. */
        void itemAcao(String acao, Double percentual) throws Exception {
            for (String trecho : quebrarTextoPdf(acao + " - " + formatarPercentual(percentual), 90)) {
                garantirEspaco(14);
                escreverLinha(trecho, PDType1Font.HELVETICA_BOLD, 10, margem);
            }
            y -= 8;
        }

        /** Item completo: título, tarefas com responsável/prazo, comparação entre departamentos e justificativa. */
        void itemAcaoAnalisadaDTO(AcaoAnalisadaDTO a) throws Exception {
            if (a.precisaAtencao()) {
                content.setNonStrokingColor(192, 0, 0);
            }
            String tituloAcao = (a.precisaAtencao() ? "! " : "") + normalizarTextoPdf(a.acao()) + " - " + formatarPercentual(a.percentual());
            for (String trecho : quebrarTextoPdf(tituloAcao, 90)) {
                garantirEspaco(14);
                escreverLinha(trecho, PDType1Font.HELVETICA_BOLD, 10, margem);
            }
            if (a.precisaAtencao()) {
                content.setNonStrokingColor(0, 0, 0);
            }

            content.setNonStrokingColor(90, 90, 90);
            for (TarefaResponsavelDTO t : a.tarefas()) {
                String linha = "- " + t.titulo() + " - " + t.responsavel() + " - prazo " + t.prazo();
                for (String trecho : quebrarTextoPdf(normalizarTextoPdf(linha), 92)) {
                    garantirEspaco(12);
                    escreverLinha(trecho, PDType1Font.HELVETICA, 8, margem + 12);
                }
            }

            if (!a.outrosDepartamentos().isEmpty()) {
                String comparativo = "Também responsável: " + formatarComparativoDepartamentos(a.outrosDepartamentos());
                for (String trecho : quebrarTextoPdf(normalizarTextoPdf(comparativo), 92)) {
                    garantirEspaco(12);
                    escreverLinha(trecho, PDType1Font.HELVETICA_OBLIQUE, 8, margem + 12);
                }
            }
            content.setNonStrokingColor(0, 0, 0);

            content.setNonStrokingColor(90, 90, 90);
            for (String trecho : quebrarTextoPdf(normalizarTextoPdf(a.justificativa()), 92)) {
                garantirEspaco(13);
                escreverLinha(trecho, PDType1Font.HELVETICA_OBLIQUE, 9, margem + 12);
            }
            content.setNonStrokingColor(0, 0, 0);
            y -= 8;
        }

        void tabelaIndicadores(List<IndicadorRelatorioDTO> indicadores) throws Exception {
            float alturaLinha = 20;
            for (IndicadorRelatorioDTO ind : indicadores) {
                garantirEspaco(alturaLinha);

                content.setNonStrokingColor(235, 240, 245);
                content.addRect(margem, y - alturaLinha + 6, larguraUtil, alturaLinha);
                content.fill();
                content.setNonStrokingColor(0, 0, 0);

                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 10);
                content.newLineAtOffset(margem + 6, y - alturaLinha + 12);
                content.showText(normalizarTextoPdf(ind.rotulo()));
                content.endText();

                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 10);
                content.newLineAtOffset(margem + larguraUtil - 100, y - alturaLinha + 12);
                content.showText(normalizarTextoPdf(ind.valor()));
                content.endText();

                y -= alturaLinha;
            }
            y -= 8;
        }

        private void escreverLinha(String texto, PDType1Font fonte, float tamanho, float x) throws Exception {
            content.beginText();
            content.setFont(fonte, tamanho);
            content.newLineAtOffset(x, y);
            content.showText(texto);
            content.endText();
            y -= tamanho + 4;
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
