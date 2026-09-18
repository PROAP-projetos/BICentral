package com.bicentral.bicentral_backend.service.ia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Memória explícita por usuário (Fase 1 do autolearning do proIAp — ver docs/tcc-mudancas-agente.md):
// o usuário pede pra lembrar algo ("sempre responda resumido") e isso entra no CONTEXTO enviado
// ao modelo em toda conversa futura dessa pessoa. Nada de extração automática de padrão nem
// retreino de modelo — só o que foi pedido explicitamente entra aqui (ver MemoriaTool).
@Service
public class MemoriaUsuarioService {

    private final JdbcTemplate jdbcTemplate;

    public MemoriaUsuarioService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS memoria_usuario (
                id BIGSERIAL PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                tipo TEXT NOT NULL,
                conteudo TEXT NOT NULL,
                ativo BOOLEAN NOT NULL DEFAULT TRUE,
                origem TEXT NOT NULL DEFAULT 'explicita',
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    // Nunca apaga de verdade — se idParaSubstituir vier preenchido (a IA já viu a memória antiga
    // no contexto e reconheceu o conflito), só desativa ela e insere a nova, mantendo o histórico
    // pra dar pra auditar depois "por que o agente tá fazendo isso".
    @Transactional
    public Long salvar(Long usuarioId, String tipo, String conteudo, Long idParaSubstituir) {
        if (idParaSubstituir != null) {
            jdbcTemplate.update(
                "UPDATE memoria_usuario SET ativo = false, atualizado_em = NOW() WHERE id = ? AND usuario_id = ?",
                idParaSubstituir, usuarioId);
        }
        return jdbcTemplate.queryForObject("""
            INSERT INTO memoria_usuario (usuario_id, tipo, conteudo)
            VALUES (?, ?, ?)
            RETURNING id
            """, Long.class, usuarioId, tipo, conteudo);
    }

    public List<Map<String, Object>> listarAtivas(Long usuarioId) {
        return jdbcTemplate.queryForList("""
            SELECT id, tipo, conteudo FROM memoria_usuario
            WHERE usuario_id = ? AND ativo = true
            ORDER BY criado_em
            """, usuarioId);
    }

    // Bloco de texto pronto pra entrar no CONTEXTO enviado ao modelo (ver ProiapService) — vazio
    // se a pessoa não tiver nenhuma preferência salva, pra não poluir o prompt à toa. O id de cada
    // linha é o que a tool usa como idParaSubstituir quando uma preferência nova contradiz essa.
    public String montarBlocoMemoria(Long usuarioId) {
        List<Map<String, Object>> memorias = listarAtivas(usuarioId);
        if (memorias.isEmpty()) {
            return "";
        }
        StringBuilder texto = new StringBuilder("MEMÓRIA DO USUÁRIO (preferências que essa pessoa já pediu pra lembrar):\n");
        for (Map<String, Object> memoria : memorias) {
            texto.append("- [id ").append(memoria.get("id")).append("] ").append(memoria.get("conteudo")).append("\n");
        }
        return texto.toString();
    }
}
