package com.bicentral.bicentral_backend.service.notificacao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.PainelAtrasosDTO;
import com.bicentral.bicentral_backend.dto.notificacao.TarefaCriticaDTO;
import com.bicentral.bicentral_backend.dto.painel.GraficoSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.SerieGraficoDTO;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NotificacaoService {

    private static final DateTimeFormatter FORMATO_PRAZO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbcTemplate;

    public NotificacaoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gera notificações de rendimento (baixo/bom) para os departamentos
     * que o usuário informado gerencia, com base na execução média do PAT.
     */
    public List<NotificacaoDTO> gerarNotificacoes(Long usuarioId) {
        List<NotificacaoDTO> notificacoes = new ArrayList<>();

        double limiteBaixo = 40.0;
        double limiteBom = 75.0;

        try {
            String sqlLimites = "SELECT limite_baixo_pct, limite_bom_pct FROM configuracoes_notificacao WHERE id = 1";
            Map<String, Object> limites = jdbcTemplate.queryForMap(sqlLimites);
            limiteBaixo = ((Number) limites.get("limite_baixo_pct")).doubleValue();
            limiteBom = ((Number) limites.get("limite_bom_pct")).doubleValue();
        } catch (Exception e) {
            System.err.println(">>> AVISO: não foi possível carregar configuracoes_notificacao, usando valores padrão (40/75). Motivo: " + e.getMessage());
        }

        // Verifica quantos snapshots (datas de extração distintas) existem
        List<java.sql.Date> snapshots;
        try {
            snapshots = jdbcTemplate.query(
                "SELECT DISTINCT data_extracao FROM pat_execucao_departamento ORDER BY data_extracao DESC LIMIT 2",
                (rs, rowNum) -> rs.getDate("data_extracao")
            );
        } catch (Exception e) {
            System.err.println(">>> AVISO: coluna data_extracao ainda não existe ou consulta falhou. Rode alterar_tabela_pat_snapshots.sql. Motivo: " + e.getMessage());
            snapshots = new ArrayList<>();
        }

        boolean temTendencia = snapshots.size() >= 2;

        String sql;
        List<Map<String, Object>> resultados;

        if (temTendencia) {
            // Compara o snapshot mais recente com o anterior
            sql = """
                SELECT gd.departamento,
                       ROUND(AVG(CASE WHEN p.data_extracao = ? THEN p.percentual_execucao END) * 100, 2) AS media_atual,
                       ROUND(AVG(CASE WHEN p.data_extracao = ? THEN p.percentual_execucao END) * 100, 2) AS media_anterior
                FROM gerentes_departamento gd
                JOIN pat_execucao_departamento p ON p.departamento = gd.departamento
                WHERE gd.usuario_id = ?
                GROUP BY gd.departamento
                """;
            resultados = jdbcTemplate.queryForList(sql, snapshots.get(0), snapshots.get(1), usuarioId);
        } else {
            sql = """
                SELECT gd.departamento, ROUND(AVG(p.percentual_execucao) * 100, 2) AS media_atual
                FROM gerentes_departamento gd
                JOIN pat_execucao_departamento p ON p.departamento = gd.departamento
                WHERE gd.usuario_id = ?
                GROUP BY gd.departamento
                """;
            try {
                resultados = jdbcTemplate.queryForList(sql, usuarioId);
            } catch (Exception e) {
                System.err.println(">>> ERRO ao consultar notificações para usuarioId=" + usuarioId + ": " + e.getMessage());
                return notificacoes;
            }
        }

        for (Map<String, Object> row : resultados) {
            String depto = (String) row.get("departamento");
            Object mediaAtualObj = row.get("media_atual");
            if (mediaAtualObj == null) continue;
            double mediaAtual = ((Number) mediaAtualObj).doubleValue();

            if (temTendencia) {
                Object mediaAnteriorObj = row.get("media_anterior");
                if (mediaAnteriorObj != null) {
                    double mediaAnterior = ((Number) mediaAnteriorObj).doubleValue();
                    double diferenca = mediaAtual - mediaAnterior;

                    if (diferenca > 3.0) {
                        notificacoes.add(new NotificacaoDTO("📈", depto,
                                "está melhorando: subiu de " + mediaAnterior + "% para " + mediaAtual + "% no PAT.",
                                mediaAtual, List.of()));
                        continue;
                    } else if (diferenca < -3.0) {
                        notificacoes.add(new NotificacaoDTO("📉", depto,
                                "caiu de " + mediaAnterior + "% para " + mediaAtual + "% no PAT.",
                                mediaAtual, buscarTarefasCriticas(depto, 3)));
                        continue;
                    } else if (mediaAtual < limiteBaixo) {
                        notificacoes.add(new NotificacaoDTO("⏸️", depto,
                                "segue estagnada em torno de " + mediaAtual + "% no PAT, sem variação recente.",
                                mediaAtual, buscarTarefasCriticas(depto, 3)));
                        continue;
                    }
                }
            }

            // Fallback: sem histórico suficiente, usa o alerta "cru" por faixa absoluta
            if (mediaAtual < limiteBaixo) {
                notificacoes.add(new NotificacaoDTO("⚠️", depto,
                        "está com execução baixa no PAT (" + mediaAtual + "%).",
                        mediaAtual, buscarTarefasCriticas(depto, 3)));
            } else if (mediaAtual > limiteBom) {
                notificacoes.add(new NotificacaoDTO("✅", depto,
                        "está com ótima execução no PAT (" + mediaAtual + "%). Parabéns à equipe!",
                        mediaAtual, List.of()));
            }
        }

        System.out.println(">>> NOTIFICACOES geradas para usuarioId=" + usuarioId + ": " + notificacoes.size() + " (com tendência: " + temTendencia + ")");
        return notificacoes;
    }

    /**
     * Busca as tarefas mais críticas de um departamento (atrasadas primeiro,
     * depois as de menor percentual) para dar contexto acionável às notificações.
     */
    private List<TarefaCriticaDTO> buscarTarefasCriticas(String departamento, int limite) {
        List<Map<String, Object>> tarefas;
        try {
            tarefas = jdbcTemplate.queryForList("""
                SELECT
                    dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    dados_completos->>'Responsável' AS responsavel,
                    NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual,
                    to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final
                FROM pat_tarefas
                WHERE departamento ILIKE ?
                ORDER BY
                    (to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                        AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100) DESC,
                    percentual ASC NULLS LAST
                LIMIT ?
                """, "%" + departamento.trim() + "%", limite);
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar tarefas críticas de '" + departamento + "': " + e.getMessage());
            return List.of();
        }

        List<TarefaCriticaDTO> resultado = new ArrayList<>();
        for (Map<String, Object> t : tarefas) {
            resultado.add(new TarefaCriticaDTO(
                    (String) t.get("titulo_tarefa"),
                    (String) t.get("responsavel"),
                    formatarPrazo((java.sql.Date) t.get("data_final"))));
        }
        return resultado;
    }

    private static String formatarPrazo(java.sql.Date data) {
        return data != null ? data.toLocalDate().format(FORMATO_PRAZO) : null;
    }

    /**
     * Monta o painel completo de atrasos de um departamento: todas as tarefas
     * atrasadas (sem limite de 3) e um gráfico de barras com a contagem de
     * atrasos por responsável. Consulta pura, sem passar por LLM.
     */
    public PainelAtrasosDTO gerarPainelAtrasos(String departamento) {
        List<TarefaCriticaDTO> tarefas;
        try {
            List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                SELECT
                    dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    dados_completos->>'Responsável' AS responsavel,
                    to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final
                FROM pat_tarefas
                WHERE departamento ILIKE ?
                    AND to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                    AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100
                ORDER BY data_final ASC NULLS LAST
                LIMIT 100
                """, "%" + departamento.trim() + "%");

            tarefas = new ArrayList<>();
            for (Map<String, Object> t : linhas) {
                tarefas.add(new TarefaCriticaDTO(
                        (String) t.get("titulo_tarefa"),
                        (String) t.get("responsavel"),
                        formatarPrazo((java.sql.Date) t.get("data_final"))));
            }
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao buscar tarefas atrasadas de '" + departamento + "': " + e.getMessage());
            tarefas = List.of();
        }

        List<String> responsaveis = new ArrayList<>();
        List<Number> totais = new ArrayList<>();
        try {
            List<Map<String, Object>> agregados = jdbcTemplate.queryForList("""
                SELECT dados_completos->>'Responsável' AS responsavel, COUNT(*) AS total
                FROM pat_tarefas
                WHERE departamento ILIKE ?
                    AND to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                    AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100
                GROUP BY responsavel
                ORDER BY total DESC
                LIMIT 10
                """, "%" + departamento.trim() + "%");

            for (Map<String, Object> a : agregados) {
                responsaveis.add((String) a.get("responsavel"));
                totais.add((Number) a.get("total"));
            }
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao agregar atrasos por responsável de '" + departamento + "': " + e.getMessage());
        }

        GraficoSpecDTO grafico = new GraficoSpecDTO(
                "", "grafico", "Tarefas atrasadas por responsável — " + departamento, "bar",
                responsaveis, List.of(new SerieGraficoDTO("Tarefas atrasadas", totais)), false);

        return new PainelAtrasosDTO(departamento, grafico, tarefas);
    }
}
