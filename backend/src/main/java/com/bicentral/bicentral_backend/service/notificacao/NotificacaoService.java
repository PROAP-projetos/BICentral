package com.bicentral.bicentral_backend.service.notificacao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NotificacaoService {

    private final JdbcTemplate jdbcTemplate;

    public NotificacaoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gera notificações de rendimento (baixo/bom) para os departamentos
     * que o usuário informado gerencia, com base na execução média do PAT.
     */
    public List<String> gerarNotificacoes(Long usuarioId) {
        List<String> notificacoes = new ArrayList<>();

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
                        notificacoes.add("📈 " + depto + " está melhorando: subiu de " + mediaAnterior + "% para " + mediaAtual + "% no PAT.");
                        continue;
                    } else if (diferenca < -3.0) {
                        notificacoes.add("📉 " + depto + " caiu de " + mediaAnterior + "% para " + mediaAtual + "% no PAT. Vale conferir o que mudou.");
                        continue;
                    } else if (mediaAtual < limiteBaixo) {
                        notificacoes.add("⏸️ " + depto + " segue estagnada em torno de " + mediaAtual + "% no PAT, sem variação recente.");
                        continue;
                    }
                }
            }

            // Fallback: sem histórico suficiente, usa o alerta "cru" por faixa absoluta
            if (mediaAtual < limiteBaixo) {
                notificacoes.add("⚠️ " + depto + " está com execução baixa no PAT (" + mediaAtual + "%). Vale revisar as ações pendentes.");
            } else if (mediaAtual > limiteBom) {
                notificacoes.add("✅ " + depto + " está com ótima execução no PAT (" + mediaAtual + "%). Parabéns à equipe!");
            }
        }

        System.out.println(">>> NOTIFICACOES geradas para usuarioId=" + usuarioId + ": " + notificacoes.size() + " (com tendência: " + temTendencia + ")");
        return notificacoes;
    }
}