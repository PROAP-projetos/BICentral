package com.bicentral.bicentral_backend.service.ia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TermoUsoService {

    // Subir a cada mudança no texto do termo: todos aceitam de novo.
    public static final String VERSAO_ATUAL = "1.2";

    private final JdbcTemplate jdbcTemplate;

    public TermoUsoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS termo_uso_aceite (
                usuario_id BIGINT NOT NULL,
                versao TEXT NOT NULL,
                aceito_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                PRIMARY KEY (usuario_id, versao)
            )
            """);
        // Sem política: a API REST do Supabase fica bloqueada; o backend, dono da tabela, não passa pelo RLS.
        jdbcTemplate.execute("ALTER TABLE termo_uso_aceite ENABLE ROW LEVEL SECURITY");
    }

    public boolean aceitouVersaoAtual(Long usuarioId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM termo_uso_aceite WHERE usuario_id = ? AND versao = ?",
                Integer.class, usuarioId, VERSAO_ATUAL);
        return total != null && total > 0;
    }

    public void registrarAceite(Long usuarioId) {
        jdbcTemplate.update("""
            INSERT INTO termo_uso_aceite (usuario_id, versao) VALUES (?, ?)
            ON CONFLICT (usuario_id, versao) DO NOTHING
            """, usuarioId, VERSAO_ATUAL);
    }
}
