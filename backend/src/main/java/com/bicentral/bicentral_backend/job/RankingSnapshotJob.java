package com.bicentral.bicentral_backend.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

@Component
public class RankingSnapshotJob {
    private final JdbcTemplate jdbcTemplate;

    public RankingSnapshotJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabela();
    }

    private void garantirTabela(){
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ranking_pat_snapshots(
                    id BIGSERIAL PRIMARY KEY,
                    data_snapshot DATE NOT NULL,
                    departamento TEXT NOT NULL,
                    tipo_unidade TEXT,
                    media_execucao_pct NUMERIC,
                    qtd_acoes INT,
                    UNIQUE (data_snapshot, departamento)
                )
                """);
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000L, initialDelay = 100 * 1000L)
    public void executar() {
        System.out.println(">>> JOB RANKING SNAPSHOT: Iniciando...");

        List<Map<String, Object>> ranking = jdbcTemplate.queryForList("""
                SELECT departamento, tipo_unidade,
                       ROUND(AVG(percentual_execucao) * 100, 2) AS media_execucao_pct,
                       COUNT(*) AS qtd_acoes
                FROM pat_execucao_departamento
                GROUP BY departamento, tipo_unidade
                """);

        for (Map<String, Object> linha : ranking) {
            jdbcTemplate.update(
                    """
                            INSERT INTO ranking_pat_snapshots (data_snapshot, departamento, tipo_unidade, media_execucao_pct, qtd_acoes)
                            VALUES (CURRENT_DATE, ?, ?, ?, ?)
                            ON CONFLICT (data_snapshot, departamento) DO UPDATE SET
                                tipo_unidade = EXCLUDED.tipo_unidade,
                                media_execucao_pct = EXCLUDED.media_execucao_pct,
                                qtd_acoes = EXCLUDED.qtd_acoes
                            """,
                    linha.get("departamento"), linha.get("tipo_unidade"),
                    linha.get("media_execucao_pct"), linha.get("qtd_acoes"));
        }

        System.out.println(">>> JOB RANKING SNAPSHOT: " + ranking.size() + " departamento(s) salvos.");
    }
}

