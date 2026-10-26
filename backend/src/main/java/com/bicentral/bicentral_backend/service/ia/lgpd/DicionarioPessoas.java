package com.bicentral.bicentral_backend.service.ia.lgpd;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DicionarioPessoas {

    // usuario.username fica de fora: é apelido de login ("admin", "proap") e trocaria palavras comuns.
    private static final String SQL_NOMES = """
            SELECT DISTINCT trim(dados_completos->>'Responsável') AS nome
            FROM pat_tarefas_todas
            WHERE coalesce(trim(dados_completos->>'Responsável'), '') <> ''
            UNION
            SELECT DISTINCT trim(nome_responsavel)
            FROM usuario_responsavel
            WHERE coalesce(trim(nome_responsavel), '') <> ''
            """;

    private final JdbcTemplate jdbcTemplate;
    private volatile Pseudonimizador atual = new Pseudonimizador(List.of());

    public DicionarioPessoas(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        recarregar();
    }

    public Pseudonimizador pseudonimizador() {
        return atual;
    }

    // O sync da UFT roda em outro processo e não avisa quando chega nome novo.
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void recarregar() {
        try {
            List<String> nomes = jdbcTemplate.queryForList(SQL_NOMES, String.class);
            atual = new Pseudonimizador(nomes);
            System.out.println(">>> LGPD: dicionário de pessoas carregado com " + nomes.size() + " nomes ("
                    + atual.totalTermos() + " termos).");
        } catch (Exception e) {
            System.err.println(">>> LGPD: falha ao carregar dicionário de pessoas, mantendo o anterior: " + e.getMessage());
        }
    }
}
