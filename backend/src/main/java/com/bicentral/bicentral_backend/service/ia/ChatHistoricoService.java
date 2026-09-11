package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.ia.MensagemHistoricoDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoCompartilhadaDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoResumoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatHistoricoService {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ChatHistoricoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabelas();
    }

    private void garantirTabelas() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chat_sessao (
                id TEXT PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                titulo TEXT NOT NULL,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chat_mensagem (
                id BIGSERIAL PRIMARY KEY,
                sessao_id TEXT NOT NULL REFERENCES chat_sessao(id),
                remetente TEXT NOT NULL,
                texto TEXT,
                spec TEXT,
                fontes TEXT,
                sugestoes TEXT,
                interacao_id BIGINT,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("ALTER TABLE chat_sessao ADD COLUMN IF NOT EXISTS fixado BOOLEAN NOT NULL DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE chat_sessao ADD COLUMN IF NOT EXISTS compartilhar_token TEXT");
        // Índice único parcial — só entre tokens não-nulos, já que a maioria das sessões nunca é
        // compartilhada e ficaria com o valor NULL (permitido repetir).
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS chat_sessao_token_idx
            ON chat_sessao(compartilhar_token) WHERE compartilhar_token IS NOT NULL
            """);
    }

    private void garantirSessao(String sessaoId, Long usuarioId, String tituloSugerido) {
        Integer existe = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_sessao WHERE id = ?", Integer.class, sessaoId);
        if (existe != null && existe > 0) {
            jdbcTemplate.update("UPDATE chat_sessao SET atualizado_em = NOW() WHERE id = ?", sessaoId);
            return;
        }
        String titulo = tituloSugerido.length() > 25 ? tituloSugerido.substring(0, 25) + "..." : tituloSugerido;
        jdbcTemplate.update(
                "INSERT INTO chat_sessao (id, usuario_id, titulo) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                sessaoId, usuarioId, titulo);
    }

    public void salvarUser(String sessaoId, Long usuarioId, String texto) {
        garantirSessao(sessaoId, usuarioId, texto);
        inserirMensagem(sessaoId, "user", texto, null, null, null, null);
    }

    public void salvarBot(String sessaoId, Long usuarioId, String texto, Object spec,
            List<String> fontes, List<String> sugestoes, Long interacaoId) {
        garantirSessao(sessaoId, usuarioId, texto);
        inserirMensagem(sessaoId, "bot", texto, toJson(spec), toJson(fontes), toJson(sugestoes), interacaoId);
    }

    private void inserirMensagem(String sessaoId, String remetente, String texto,
            String specJson, String fontesJson, String sugestoesJson, Long interacaoId) {
        jdbcTemplate.update("""
            INSERT INTO chat_mensagem (sessao_id, remetente, texto, spec, fontes, sugestoes, interacao_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, sessaoId, remetente, texto, specJson, fontesJson, sugestoesJson, interacaoId);
    }

    private String toJson(Object valor) {
        if (valor == null) return null;
        try {
            return MAPPER.writeValueAsString(valor);
        } catch (Exception e) {
            return null;
        }
    }

    public List<SessaoResumoDTO> listarSessoes(Long usuarioId) {
        return jdbcTemplate.query("""
            SELECT id, titulo, criado_em, fixado FROM chat_sessao
            WHERE usuario_id = ?
            ORDER BY fixado DESC, atualizado_em DESC
            """, (rs, rowNum) -> new SessaoResumoDTO(
                rs.getString("id"), rs.getString("titulo"), rs.getObject("criado_em", OffsetDateTime.class),
                rs.getBoolean("fixado")
        ), usuarioId);
    }

    public List<MensagemHistoricoDTO> listarMensagens(String sessaoId, Long usuarioId) {
        confirmarDono(sessaoId, usuarioId);
        return mapearMensagens(sessaoId);
    }

    @Transactional
    public void excluirSessao(String sessaoId, Long usuarioId) {
        confirmarDono(sessaoId, usuarioId);
        jdbcTemplate.update("DELETE FROM chat_mensagem WHERE sessao_id = ?", sessaoId);
        jdbcTemplate.update("DELETE FROM chat_sessao WHERE id = ?", sessaoId);
    }

    public void renomear(String sessaoId, Long usuarioId, String novoTitulo) {
        confirmarDono(sessaoId, usuarioId);
        String titulo = novoTitulo == null || novoTitulo.isBlank() ? "Nova Conversa" : novoTitulo.trim();
        jdbcTemplate.update("UPDATE chat_sessao SET titulo = ? WHERE id = ?", titulo, sessaoId);
    }

    public void fixar(String sessaoId, Long usuarioId, boolean fixado) {
        confirmarDono(sessaoId, usuarioId);
        jdbcTemplate.update("UPDATE chat_sessao SET fixado = ? WHERE id = ?", fixado, sessaoId);
    }

    // Gera o token de compartilhamento na primeira vez (link público, sem exigir login pra
    // abrir — ver ProiapController) e devolve o mesmo token em chamadas seguintes, pra não
    // invalidar um link que já foi mandado pra alguém.
    public String compartilhar(String sessaoId, Long usuarioId) {
        confirmarDono(sessaoId, usuarioId);
        String tokenExistente;
        try {
            tokenExistente = jdbcTemplate.queryForObject(
                    "SELECT compartilhar_token FROM chat_sessao WHERE id = ?", String.class, sessaoId);
        } catch (EmptyResultDataAccessException e) {
            tokenExistente = null;
        }
        if (tokenExistente != null) {
            return tokenExistente;
        }
        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("UPDATE chat_sessao SET compartilhar_token = ? WHERE id = ?", token, sessaoId);
        return token;
    }

    // Sem checagem de dono de propósito — é o endpoint público (link de compartilhar), qualquer
    // pessoa com o token consegue ler. Por isso o token é um UUID aleatório, não o id da sessão.
    public SessaoCompartilhadaDTO buscarCompartilhada(String token) {
        String sessaoId;
        String titulo;
        try {
            var linha = jdbcTemplate.queryForMap(
                    "SELECT id, titulo FROM chat_sessao WHERE compartilhar_token = ?", token);
            sessaoId = (String) linha.get("id");
            titulo = (String) linha.get("titulo");
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link inválido ou expirado.");
        }
        return new SessaoCompartilhadaDTO(titulo, mapearMensagens(sessaoId));
    }

    private void confirmarDono(String sessaoId, Long usuarioId) {
        // Confere que a sessão é do próprio usuário antes de deixar ler/alterar qualquer coisa —
        // sem isso, adivinhar um id de sessão (é só um timestamp) deixaria mexer no chat de outra
        // pessoa.
        Integer dono = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_sessao WHERE id = ? AND usuario_id = ?", Integer.class, sessaoId, usuarioId);
        if (dono == null || dono == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada.");
        }
    }

    private List<MensagemHistoricoDTO> mapearMensagens(String sessaoId) {
        return jdbcTemplate.query("""
            SELECT m.remetente, m.texto, m.spec, m.fontes, m.sugestoes, m.interacao_id,
                   (l.feedback IS NOT NULL) AS feedback_enviado
            FROM chat_mensagem m
            LEFT JOIN interacao_ia_log l ON l.id = m.interacao_id
            WHERE m.sessao_id = ?
            ORDER BY m.id ASC
            """, (rs, rowNum) -> new MensagemHistoricoDTO(
                rs.getString("remetente"),
                rs.getString("texto"),
                parseJson(rs.getString("spec")),
                parseJsonLista(rs.getString("fontes")),
                parseJsonLista(rs.getString("sugestoes")),
                (Long) rs.getObject("interacao_id"),
                rs.getBoolean("feedback_enviado")
        ), sessaoId);
    }

    private Object parseJson(String json) {
        if (json == null) return null;
        try { return MAPPER.readTree(json); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonLista(String json) {
        if (json == null) return null;
        try { return MAPPER.readValue(json, List.class); } catch (Exception e) { return null; }
    }
}
