package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.diretrizes.AlinhamentoLoteIADTO;
import com.bicentral.bicentral_backend.dto.diretrizes.AlinhamentoTarefaIADTO;
import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.SituacaoAlinhamento;
import dev.langchain4j.service.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Julga cada tarefa do PAT uma vez frente às diretrizes INEP/IESGo da ação dela e guarda o veredito,
 * para o panorama de recortes grandes (PAT inteiro, departamento) ser só agregação. Quem chama é o
 * AlinhamentoDiretrizesJob, desligado por padrão.
 */
@Service
public class AlinhamentoDiretrizesService {

    public static final String SEM_DIRETRIZ = "SEM_DIRETRIZ";
    static final int MAX_TAREFAS_POR_LOTE = 30;
    static final int MAX_ACOES_POR_LOTE = 8;
    private static final int MAX_DESCRICAO = 300;

    /** Muda quando o código da ação, o título ou a descrição mudam: a tarefa volta a ser analisada. */
    public static final String SQL_HASH_TEXTO = "md5(coalesce(t.dados_completos->>'ITEM DO PAT', '') || '|'"
            + " || coalesce(t.dados_completos->>'TÍTULO DA TAREFA', '') || '|'"
            + " || coalesce(t.dados_completos->>'DESCRIÇÃO DA TAREFA', ''))";

    public static final String SQL_CODIGO_ACAO =
            "substring(t.dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*')";

    private static final Pattern CODIGO_INEP = Pattern.compile("(?i)\\bINEP\\s*(\\d+(?:\\.\\d+)*)");
    private static final Pattern CODIGO_IESGO = Pattern.compile("(?i)\\bIESGo\\s*(\\d{4})");

    record TarefaPendente(long id, String codigoAcao, String titulo, String descricao, String hash) {
    }

    record Analise(long id, String codigoAcao, String situacao, List<String> diretrizes, String justificativa,
                   String hash) {
    }

    public record ResumoExecucao(int lotes, int tarefas, int tokensEntrada, int tokensSaida) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final AgenteAlinhamento agente;
    private final DiretrizesService diretrizesService;
    private final UsoIaService usoIaService;

    public AlinhamentoDiretrizesService(JdbcTemplate jdbcTemplate, AgenteAlinhamento agente,
            DiretrizesService diretrizesService, UsoIaService usoIaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.agente = agente;
        this.diretrizesService = diretrizesService;
        this.usoIaService = usoIaService;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS alinhamento_tarefa (
                tarefa_id BIGINT PRIMARY KEY,
                codigo_acao TEXT,
                situacao TEXT NOT NULL,
                diretrizes TEXT,
                justificativa TEXT,
                hash_texto TEXT NOT NULL,
                versao_base TEXT NOT NULL,
                analisado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("ALTER TABLE alinhamento_tarefa ENABLE ROW LEVEL SECURITY");
    }

    /** Analisa até {@code maxLotes} lotes de tarefas novas, alteradas ou de uma versão anterior da base. */
    public ResumoExecucao processarPendentes(int maxLotes) {
        List<TarefaPendente> pendentes = buscarPendentes(maxLotes * MAX_TAREFAS_POR_LOTE);
        if (pendentes.isEmpty()) {
            return new ResumoExecucao(0, 0, 0, 0);
        }

        // Sem diretriz na base não há o que julgar: grava direto, sem gastar token.
        List<TarefaPendente> avaliaveis = new ArrayList<>();
        List<Analise> semDiretriz = new ArrayList<>();
        for (TarefaPendente tarefa : pendentes) {
            if (DiretrizesService.temDiretrizes(diretrizesService.buscar(tarefa.codigoAcao()).orElse(null))) {
                avaliaveis.add(tarefa);
            } else {
                semDiretriz.add(new Analise(tarefa.id(), tarefa.codigoAcao(), SEM_DIRETRIZ, List.of(), null, tarefa.hash()));
            }
        }
        salvar(semDiretriz);

        int lotes = 0;
        int tarefas = semDiretriz.size();
        int tokensEntrada = 0;
        int tokensSaida = 0;
        for (List<TarefaPendente> lote : montarLotes(avaliaveis)) {
            if (lotes >= maxLotes) {
                break;
            }
            try {
                Result<AlinhamentoLoteIADTO> resultado = agente.avaliarLote(textoDoLote(lote));
                usoIaService.registrarUso(null, "job-alinhamento",
                        "[job] alinhamento INEP/IESGo: lote de " + lote.size() + " tarefas", null, resultado.tokenUsage());
                List<Analise> analises = validar(lote, resultado.content(), diretrizesService);
                salvar(analises);
                lotes++;
                tarefas += analises.size();
                if (resultado.tokenUsage() != null) {
                    tokensEntrada += valor(resultado.tokenUsage().inputTokenCount());
                    tokensSaida += valor(resultado.tokenUsage().outputTokenCount());
                }
            } catch (Exception e) {
                // Falha costuma ser cota ou chave da API: insistir só gasta. Os pendentes voltam na próxima execução.
                System.err.println(">>> ALINHAMENTO: lote falhou, parando esta execução: " + e.getMessage());
                break;
            }
        }
        return new ResumoExecucao(lotes, tarefas, tokensEntrada, tokensSaida);
    }

    private List<TarefaPendente> buscarPendentes(int limite) {
        return jdbcTemplate.query("""
            SELECT p.* FROM (
                SELECT t.id, %s AS codigo_acao,
                       t.dados_completos->>'TÍTULO DA TAREFA' AS titulo,
                       t.dados_completos->>'DESCRIÇÃO DA TAREFA' AS descricao,
                       %s AS hash_texto
                FROM pat_tarefas t
            ) p
            LEFT JOIN alinhamento_tarefa a ON a.tarefa_id = p.id
            WHERE a.tarefa_id IS NULL OR a.hash_texto <> p.hash_texto OR a.versao_base <> ?
            ORDER BY p.codigo_acao NULLS LAST, p.id
            LIMIT ?
            """.formatted(SQL_CODIGO_ACAO, SQL_HASH_TEXTO),
                (rs, i) -> new TarefaPendente(rs.getLong("id"), rs.getString("codigo_acao"), rs.getString("titulo"),
                        rs.getString("descricao"), rs.getString("hash_texto")),
                diretrizesService.versao(), limite);
    }

    /** Espera as tarefas ordenadas por ação; corta o lote no limite de tarefas ou de ações distintas. */
    static List<List<TarefaPendente>> montarLotes(List<TarefaPendente> tarefas) {
        List<List<TarefaPendente>> lotes = new ArrayList<>();
        List<TarefaPendente> atual = new ArrayList<>();
        Set<String> acoesDoLote = new LinkedHashSet<>();
        for (TarefaPendente tarefa : tarefas) {
            boolean acaoNova = !acoesDoLote.contains(tarefa.codigoAcao());
            if (atual.size() == MAX_TAREFAS_POR_LOTE || (acaoNova && acoesDoLote.size() == MAX_ACOES_POR_LOTE)) {
                lotes.add(atual);
                atual = new ArrayList<>();
                acoesDoLote = new LinkedHashSet<>();
            }
            atual.add(tarefa);
            acoesDoLote.add(tarefa.codigoAcao());
        }
        if (!atual.isEmpty()) {
            lotes.add(atual);
        }
        return lotes;
    }

    private String textoDoLote(List<TarefaPendente> lote) {
        Map<String, List<TarefaPendente>> porAcao = new LinkedHashMap<>();
        for (TarefaPendente tarefa : lote) {
            porAcao.computeIfAbsent(tarefa.codigoAcao(), c -> new ArrayList<>()).add(tarefa);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<TarefaPendente>> entrada : porAcao.entrySet()) {
            DiretrizesAcaoDTO acao = diretrizesService.buscar(entrada.getKey()).orElseThrow();
            sb.append("### Ação ").append(entrada.getKey()).append(" — ").append(acao.acao()).append("\n")
              .append(DiretrizesService.descrever(acao))
              .append("Tarefas:\n");
            for (TarefaPendente tarefa : entrada.getValue()) {
                String descricao = tarefa.descricao() == null || tarefa.descricao().isBlank()
                        ? "(sem descrição)" : resumir(tarefa.descricao().trim());
                sb.append("- [id ").append(tarefa.id()).append("] \"").append(tarefa.titulo())
                  .append("\". Descrição: ").append(descricao).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Fica só com tarefas do lote e códigos da ação de cada uma. "Alinhada" sem nenhum código válido vira
     * sem evidência: um veredito positivo que não aponta diretriz não dá para conferir.
     */
    static List<Analise> validar(List<TarefaPendente> lote, AlinhamentoLoteIADTO resposta, DiretrizesService diretrizes) {
        Map<Long, TarefaPendente> porId = new LinkedHashMap<>();
        lote.forEach(t -> porId.put(t.id(), t));

        Map<Long, Analise> analises = new LinkedHashMap<>();
        List<AlinhamentoTarefaIADTO> itens = resposta == null || resposta.tarefas() == null ? List.of() : resposta.tarefas();
        for (AlinhamentoTarefaIADTO item : itens) {
            TarefaPendente tarefa = item.id() == null ? null : porId.get(item.id());
            if (tarefa == null || item.situacao() == null || analises.containsKey(tarefa.id())) {
                continue;
            }
            Set<String> permitidos = diretrizes.buscar(tarefa.codigoAcao()).map(DiretrizesService::codigos).orElse(Set.of());
            List<String> citados = new ArrayList<>();
            if (item.situacao() != SituacaoAlinhamento.SEM_EVIDENCIA) {
                for (String codigo : normalizarCodigos(item.diretrizes())) {
                    if (permitidos.contains(codigo)) {
                        citados.add(codigo);
                    }
                }
            }
            String situacao = item.situacao().name();
            String justificativa = item.justificativa();
            if (item.situacao() != SituacaoAlinhamento.SEM_EVIDENCIA && citados.isEmpty()) {
                situacao = SituacaoAlinhamento.SEM_EVIDENCIA.name();
                justificativa = "A análise não apontou diretriz da ação que a tarefa cubra.";
            }
            analises.put(tarefa.id(), new Analise(tarefa.id(), tarefa.codigoAcao(), situacao, citados, justificativa, tarefa.hash()));
        }
        return new ArrayList<>(analises.values());
    }

    static List<String> normalizarCodigos(List<String> codigos) {
        Set<String> normalizados = new LinkedHashSet<>();
        for (String codigo : codigos == null ? List.<String>of() : codigos) {
            if (codigo == null) {
                continue;
            }
            Matcher inep = CODIGO_INEP.matcher(codigo);
            while (inep.find()) {
                normalizados.add("INEP " + inep.group(1));
            }
            Matcher iesgo = CODIGO_IESGO.matcher(codigo);
            while (iesgo.find()) {
                normalizados.add("IESGo " + iesgo.group(1));
            }
        }
        return new ArrayList<>(normalizados);
    }

    private void salvar(List<Analise> analises) {
        if (analises.isEmpty()) {
            return;
        }
        String versao = diretrizesService.versao();
        jdbcTemplate.batchUpdate("""
            INSERT INTO alinhamento_tarefa (tarefa_id, codigo_acao, situacao, diretrizes, justificativa, hash_texto, versao_base, analisado_em)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (tarefa_id) DO UPDATE SET
                codigo_acao = EXCLUDED.codigo_acao, situacao = EXCLUDED.situacao, diretrizes = EXCLUDED.diretrizes,
                justificativa = EXCLUDED.justificativa, hash_texto = EXCLUDED.hash_texto,
                versao_base = EXCLUDED.versao_base, analisado_em = NOW()
            """, analises.stream().map(a -> new Object[]{
                a.id(), a.codigoAcao(), a.situacao(), String.join("; ", a.diretrizes()), a.justificativa(), a.hash(), versao
            }).toList());
    }

    private static String resumir(String texto) {
        return texto.length() <= MAX_DESCRICAO ? texto : texto.substring(0, MAX_DESCRICAO) + "...";
    }

    private static int valor(Integer numero) {
        return numero == null ? 0 : numero;
    }
}
