package com.bicentral.bicentral_backend.service.painel;

import com.bicentral.bicentral_backend.dto.painel.PainelIaDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.SalvarPainelIaRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

// Painel gerado pela IA no chat, salvo pelo usuário. Guarda a especificação inteira como texto
// (mesmo padrão que RelatorioService já usa pra guardar RelatorioEstruturadoDTO) — não precisa
// quebrar em tabelas relacionais, é sempre lido de volta inteiro, nunca consultado por dentro.
@Service
public class PainelIaService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public PainelIaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS painel_ia (
                id BIGSERIAL PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                titulo TEXT NOT NULL,
                especificacao TEXT NOT NULL,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    public PainelIaDTO salvar(Long usuarioId, SalvarPainelIaRequestDTO request) {
        String especificacaoJson;
        try {
            especificacaoJson = MAPPER.writeValueAsString(request.especificacao());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Especificação de painel inválida");
        }

        Map<String, Object> linha = jdbcTemplate.queryForMap("""
            INSERT INTO painel_ia (usuario_id, titulo, especificacao)
            VALUES (?, ?, ?)
            RETURNING id, criado_em
            """, usuarioId, request.titulo(), especificacaoJson);

        return new PainelIaDTO(
                ((Number) linha.get("id")).longValue(),
                request.titulo(),
                request.especificacao(),
                paraOffsetDateTime(linha.get("criado_em")));
    }

    public List<PainelIaDTO> listarPorUsuario(Long usuarioId) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
            SELECT id, titulo, especificacao, criado_em
            FROM painel_ia
            WHERE usuario_id = ?
            ORDER BY criado_em DESC
            """, usuarioId);

        return linhas.stream().map(this::mapearLinha).toList();
    }

    public void excluir(Long usuarioId, Long id) {
        int removidos = jdbcTemplate.update(
                "DELETE FROM painel_ia WHERE id = ? AND usuario_id = ?", id, usuarioId);
        if (removidos == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado");
        }
    }

    private PainelIaDTO mapearLinha(Map<String, Object> linha) {
        try {
            PainelSpecDTO especificacao = MAPPER.readValue((String) linha.get("especificacao"), PainelSpecDTO.class);
            return new PainelIaDTO(
                    ((Number) linha.get("id")).longValue(),
                    (String) linha.get("titulo"),
                    especificacao,
                    paraOffsetDateTime(linha.get("criado_em")));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Painel salvo em formato inválido: " + e.getMessage());
        }
    }

    // O driver do Postgres devolve TIMESTAMPTZ como java.sql.Timestamp via queryForMap/queryForList
    // (não como OffsetDateTime direto) — sem essa conversão explícita dá ClassCastException.
    private static OffsetDateTime paraOffsetDateTime(Object valor) {
        if (valor instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (valor instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
        }
        throw new IllegalStateException("Tipo de data inesperado para criado_em: "
                + (valor == null ? "null" : valor.getClass()));
    }
}
