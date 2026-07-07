package com.bicentral.bicentral_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdminService {

    private static final long BOOTSTRAP_ADMIN_ID = 22L;

    private final JdbcTemplate jdbcTemplate;

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabelaAdmins();
    }

    private void garantirTabelaAdmins() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS admins_sistema (
                usuario_id BIGINT PRIMARY KEY
            )
            """);
        jdbcTemplate.update("""
            INSERT INTO admins_sistema (usuario_id)
            VALUES (?)
            ON CONFLICT (usuario_id) DO NOTHING
            """, BOOTSTRAP_ADMIN_ID);
    }

    public boolean isAdmin(Long usuarioId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admins_sistema WHERE usuario_id = ?",
                Integer.class,
                usuarioId
        );
        return total != null && total > 0;
    }

    public void exigirAdmin(Long usuarioId) {
        if (!isAdmin(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sem acesso administrativo");
        }
    }

    public List<UsuarioResumoDTO> listarUsuarios(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        return jdbcTemplate.query("""
            SELECT id, username AS nome, email
            FROM usuario
            ORDER BY username
            """, (rs, rowNum) -> new UsuarioResumoDTO(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("email")
        ));
    }

    public List<AdminSistemaDTO> listarAdmins(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        return jdbcTemplate.query("""
            SELECT a.usuario_id, u.username AS nome, u.email
            FROM admins_sistema a
            LEFT JOIN usuario u ON u.id = a.usuario_id
            ORDER BY COALESCE(u.username, a.usuario_id::text)
            """, (rs, rowNum) -> new AdminSistemaDTO(
                rs.getLong("usuario_id"),
                rs.getString("nome"),
                rs.getString("email")
        ));
    }

    @Transactional
    public void adicionarAdmin(Long usuarioLogadoId, Long usuarioId) {
        exigirAdmin(usuarioLogadoId);
        validarUsuarioExistente(usuarioId);
        jdbcTemplate.update("""
            INSERT INTO admins_sistema (usuario_id)
            VALUES (?)
            ON CONFLICT (usuario_id) DO NOTHING
            """, usuarioId);
    }

    @Transactional
    public void removerAdmin(Long usuarioLogadoId, Long usuarioId) {
        exigirAdmin(usuarioLogadoId);
        if (usuarioLogadoId.equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voce nao pode remover seu proprio acesso administrativo");
        }
        jdbcTemplate.update("DELETE FROM admins_sistema WHERE usuario_id = ?", usuarioId);
    }

    public List<GerenteDepartamentoDTO> listarGerentes(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        return jdbcTemplate.query("""
            SELECT gd.id, gd.usuario_id, u.username AS usuario_nome, u.email AS usuario_email,
                   gd.departamento, gd.tipo_unidade, gd.created_at
            FROM gerentes_departamento gd
            LEFT JOIN usuario u ON u.id = gd.usuario_id
            ORDER BY gd.departamento, u.username
            """, (rs, rowNum) -> new GerenteDepartamentoDTO(
                rs.getLong("id"),
                rs.getLong("usuario_id"),
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("departamento"),
                rs.getString("tipo_unidade"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    @Transactional
    public GerenteDepartamentoDTO adicionarGerente(Long usuarioLogadoId, GerenteDepartamentoRequestDTO request) {
        exigirAdmin(usuarioLogadoId);
        validarUsuarioExistente(request.usuarioId());
        validarGerenteRequest(request);

        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO gerentes_departamento (usuario_id, departamento, tipo_unidade)
            VALUES (?, ?, ?)
            RETURNING id
            """, Long.class, request.usuarioId(), request.departamento().trim(), request.tipoUnidade());

        return buscarGerentePorId(id);
    }

    @Transactional
    public void removerGerente(Long usuarioLogadoId, Long id) {
        exigirAdmin(usuarioLogadoId);
        int removidos = jdbcTemplate.update("DELETE FROM gerentes_departamento WHERE id = ?", id);
        if (removidos == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculo de gerente nao encontrado");
        }
    }

    public ConfiguracaoNotificacaoDTO buscarConfiguracoesNotificacao(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        garantirConfiguracaoPadrao();
        return jdbcTemplate.queryForObject("""
            SELECT id, limite_baixo_pct, limite_bom_pct
            FROM configuracoes_notificacao
            WHERE id = 1
            """, (rs, rowNum) -> new ConfiguracaoNotificacaoDTO(
                rs.getInt("id"),
                rs.getBigDecimal("limite_baixo_pct"),
                rs.getBigDecimal("limite_bom_pct")
        ));
    }

    @Transactional
    public ConfiguracaoNotificacaoDTO salvarConfiguracoesNotificacao(Long usuarioLogadoId, ConfiguracaoNotificacaoRequestDTO request) {
        exigirAdmin(usuarioLogadoId);
        if (request.limiteBaixoPct() == null || request.limiteBomPct() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dois limites percentuais");
        }
        if (request.limiteBaixoPct().compareTo(BigDecimal.ZERO) < 0
                || request.limiteBomPct().compareTo(BigDecimal.ZERO) < 0
                || request.limiteBaixoPct().compareTo(new BigDecimal("100")) > 0
                || request.limiteBomPct().compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os limites devem estar entre 0 e 100");
        }
        if (request.limiteBaixoPct().compareTo(request.limiteBomPct()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite baixo deve ser menor que o limite bom");
        }

        jdbcTemplate.update("""
            INSERT INTO configuracoes_notificacao (id, limite_baixo_pct, limite_bom_pct)
            VALUES (1, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET limite_baixo_pct = EXCLUDED.limite_baixo_pct,
                limite_bom_pct = EXCLUDED.limite_bom_pct
            """, request.limiteBaixoPct(), request.limiteBomPct());

        return buscarConfiguracoesNotificacao(usuarioLogadoId);
    }

    private void validarUsuarioExistente(Long usuarioId) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o usuario");
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ?", Integer.class, usuarioId);
        if (total == null || total == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
        }
    }

    private void validarGerenteRequest(GerenteDepartamentoRequestDTO request) {
        if (request.departamento() == null || request.departamento().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o departamento");
        }
        if (!"UA".equals(request.tipoUnidade()) && !"UG".equals(request.tipoUnidade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de unidade deve ser UA ou UG");
        }
    }

    private GerenteDepartamentoDTO buscarGerentePorId(Long id) {
        return jdbcTemplate.queryForObject("""
            SELECT gd.id, gd.usuario_id, u.username AS usuario_nome, u.email AS usuario_email,
                   gd.departamento, gd.tipo_unidade, gd.created_at
            FROM gerentes_departamento gd
            LEFT JOIN usuario u ON u.id = gd.usuario_id
            WHERE gd.id = ?
            """, (rs, rowNum) -> new GerenteDepartamentoDTO(
                rs.getLong("id"),
                rs.getLong("usuario_id"),
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("departamento"),
                rs.getString("tipo_unidade"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), id);
    }

    private void garantirConfiguracaoPadrao() {
        jdbcTemplate.update("""
            INSERT INTO configuracoes_notificacao (id, limite_baixo_pct, limite_bom_pct)
            VALUES (1, 40, 80)
            ON CONFLICT (id) DO NOTHING
            """);
    }

    public record UsuarioResumoDTO(Long id, String nome, String email) {}

    public record AdminSistemaDTO(Long usuarioId, String nome, String email) {}

    public record AdminUsuarioRequestDTO(Long usuarioId) {}

    public record GerenteDepartamentoDTO(
            Long id,
            Long usuarioId,
            String usuarioNome,
            String usuarioEmail,
            String departamento,
            String tipoUnidade,
            OffsetDateTime createdAt
    ) {}

    public record GerenteDepartamentoRequestDTO(Long usuarioId, String departamento, String tipoUnidade) {}

    public record ConfiguracaoNotificacaoDTO(Integer id, BigDecimal limiteBaixoPct, BigDecimal limiteBomPct) {}

    public record ConfiguracaoNotificacaoRequestDTO(BigDecimal limiteBaixoPct, BigDecimal limiteBomPct) {}
}
