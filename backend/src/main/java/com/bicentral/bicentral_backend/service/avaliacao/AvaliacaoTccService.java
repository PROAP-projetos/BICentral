package com.bicentral.bicentral_backend.service.avaliacao;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validação humana do experimento de avaliação do proIAp: cada avaliador marca, para cada resposta
 * do agente, se ela está correta e completa em relação à referência. Os itens são importados por
 * um admin (JSON gerado fora do sistema) e nunca apagados por aqui, para não levar junto as
 * avaliações já feitas (FK com ON DELETE CASCADE).
 */
@Service
public class AvaliacaoTccService {

    public static final Set<String> TEMPOS_MANUAIS = Set.of("<5", "5-15", "15-60", ">60", "pedir");

    private final JdbcTemplate jdbcTemplate;

    public AvaliacaoTccService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabelas();
    }

    private void garantirTabelas() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS tcc_avaliacao_item (
                id TEXT PRIMARY KEY,
                tipo INTEGER NOT NULL,
                ordem INTEGER NOT NULL,
                pergunta TEXT NOT NULL,
                referencia TEXT NOT NULL,
                resposta TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS tcc_avaliacao_resposta (
                item_id TEXT NOT NULL REFERENCES tcc_avaliacao_item(id) ON DELETE CASCADE,
                usuario_id BIGINT NOT NULL,
                correta BOOLEAN,
                completa BOOLEAN,
                tempo_manual TEXT,
                comentario TEXT,
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                PRIMARY KEY (item_id, usuario_id)
            )
            """);
    }

    public record ItemDTO(String id, int tipo, int ordem, String pergunta, String referencia, String resposta) {
    }

    /** Itens na ordem de exibição, com a avaliação do próprio usuário quando já existe. */
    public List<Map<String, Object>> listarItens(Long usuarioId) {
        return jdbcTemplate.queryForList("""
            SELECT i.id, i.tipo, i.ordem, i.pergunta, i.referencia, i.resposta,
                   r.correta, r.completa, r.tempo_manual AS "tempoManual", r.comentario
            FROM tcc_avaliacao_item i
            LEFT JOIN tcc_avaliacao_resposta r ON r.item_id = i.id AND r.usuario_id = ?
            ORDER BY i.ordem, i.id
            """, usuarioId);
    }

    public void salvarResposta(String itemId, Long usuarioId, Boolean correta, Boolean completa, String tempoManual, String comentario) {
        Integer existe = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tcc_avaliacao_item WHERE id = ?", Integer.class, itemId);
        if (existe == null || existe == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item de avaliação não encontrado.");
        }
        if (tempoManual != null && !TEMPOS_MANUAIS.contains(tempoManual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estimativa de tempo inválida.");
        }
        String comentarioLimpo = comentario == null || comentario.isBlank() ? null : comentario.trim();
        jdbcTemplate.update("""
            INSERT INTO tcc_avaliacao_resposta (item_id, usuario_id, correta, completa, tempo_manual, comentario, atualizado_em)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (item_id, usuario_id) DO UPDATE SET
                correta = EXCLUDED.correta, completa = EXCLUDED.completa,
                tempo_manual = EXCLUDED.tempo_manual, comentario = EXCLUDED.comentario,
                atualizado_em = NOW()
            """, itemId, usuarioId, correta, completa, tempoManual, comentarioLimpo);
    }

    /** Insere ou atualiza pelo id; itens fora da lista ficam como estão. */
    @Transactional
    public int importarItens(List<ItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum item enviado.");
        }
        for (ItemDTO item : itens) {
            if (item.id() == null || item.id().isBlank() || item.pergunta() == null || item.referencia() == null || item.resposta() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item incompleto: todo item precisa de id, pergunta, referencia e resposta.");
            }
            jdbcTemplate.update("""
                INSERT INTO tcc_avaliacao_item (id, tipo, ordem, pergunta, referencia, resposta)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    tipo = EXCLUDED.tipo, ordem = EXCLUDED.ordem, pergunta = EXCLUDED.pergunta,
                    referencia = EXCLUDED.referencia, resposta = EXCLUDED.resposta
                """, item.id().trim(), item.tipo(), item.ordem(), item.pergunta(), item.referencia(), item.resposta());
        }
        return itens.size();
    }

    /** Todas as avaliações, uma linha por item × avaliador, para consolidar fora do sistema. */
    public List<Map<String, Object>> resultado() {
        return jdbcTemplate.queryForList("""
            SELECT r.item_id AS "item", i.tipo, r.usuario_id AS "usuarioId", u.username AS "avaliador",
                   r.correta, r.completa, r.tempo_manual AS "tempoManual", r.comentario,
                   to_char(r.atualizado_em AT TIME ZONE 'America/Sao_Paulo', 'DD/MM/YYYY HH24:MI') AS "atualizadoEm"
            FROM tcc_avaliacao_resposta r
            JOIN tcc_avaliacao_item i ON i.id = r.item_id
            LEFT JOIN usuario u ON u.id = r.usuario_id
            ORDER BY i.ordem, r.item_id, u.username
            """);
    }
}
