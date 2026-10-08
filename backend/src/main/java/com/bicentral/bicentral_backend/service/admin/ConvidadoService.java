package com.bicentral.bicentral_backend.service.admin;

import com.bicentral.bicentral_backend.service.auth.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Convidado = usuário de fora da PROAP. Vê percentual e ações de todas as UGs, mas só vê
 * responsáveis (pessoas) dos departamentos que gerencia (gerentes_departamento). Servidor da
 * PROAP e admin não passam por essa restrição.
 */
@Service
public class ConvidadoService {

    public record ConvidadoDTO(Long usuarioId, String nome, String email, OffsetDateTime criadoEm, boolean pendente) {}

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;
    private final AdminService adminService;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    public ConvidadoService(JdbcTemplate jdbcTemplate, EmailService emailService, AdminService adminService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        this.adminService = adminService;
        garantirTabelas();
    }

    private void garantirTabelas() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS usuarios_convidados (
                usuario_id BIGINT PRIMARY KEY,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        // E-mails convidados antes de existir conta: viram convidados de verdade no cadastro
        // (ver promoverPendentes, chamado pelo UsuarioController).
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS convidados_pendentes (
                email TEXT PRIMARY KEY,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    public boolean ehConvidado(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuarios_convidados WHERE usuario_id = ?", Integer.class, usuarioId);
        return total != null && total > 0;
    }

    public boolean emailPendente(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM convidados_pendentes WHERE LOWER(email) = LOWER(?)", Integer.class, email.trim());
        return total != null && total > 0;
    }

    /**
     * Regra de escopo: pode ver responsáveis e tarefas por pessoa de um departamento?
     * Admin e quem não é convidado sempre pode. Convidado só nos departamentos que gerencia —
     * sem nenhum departamento atribuído, nunca pode.
     */
    public boolean podeVerPessoas(Long usuarioId, String departamento) {
        if (usuarioId == null) {
            return false;
        }
        if (!ehConvidado(usuarioId) || adminService.isAdmin(usuarioId)) {
            return true;
        }
        if (departamento == null || departamento.isBlank()) {
            return false;
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gerentes_departamento WHERE usuario_id = ? AND LOWER(departamento) = LOWER(?)",
                Integer.class, usuarioId, departamento.trim());
        return total != null && total > 0;
    }

    /**
     * Departamentos (em minúsculas) cujos responsáveis o usuário pode ver. null = sem restrição
     * (admin ou não convidado). Lista vazia = convidado sem departamento atribuído.
     */
    public List<String> departamentosComPessoasVisiveis(Long usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        if (!ehConvidado(usuarioId) || adminService.isAdmin(usuarioId)) {
            return null;
        }
        return jdbcTemplate.queryForList(
                "SELECT LOWER(departamento) FROM gerentes_departamento WHERE usuario_id = ?", String.class, usuarioId);
    }

    public List<ConvidadoDTO> listar() {
        return jdbcTemplate.query("""
            (SELECT c.usuario_id AS usuario_id, u.username AS nome, u.email AS email,
                    c.criado_em AS criado_em, false AS pendente
             FROM usuarios_convidados c
             LEFT JOIN usuario u ON u.id = c.usuario_id)
            UNION ALL
            (SELECT NULL::bigint, NULL::text, p.email, p.criado_em, true
             FROM convidados_pendentes p)
            ORDER BY pendente, nome NULLS LAST, email
            """, (rs, rowNum) -> new ConvidadoDTO(
                (Long) rs.getObject("usuario_id"),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getBoolean("pendente")));
    }

    // Retorna true se virou convidado confirmado na hora (já tinha conta), false se ficou
    // pendente aguardando a pessoa se cadastrar.
    @Transactional
    public boolean adicionar(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail da pessoa");
        }
        String emailNormalizado = email.trim();
        Map<String, Object> usuario;
        try {
            usuario = jdbcTemplate.queryForMap(
                    "SELECT id, username FROM usuario WHERE LOWER(email) = LOWER(?)", emailNormalizado);
        } catch (EmptyResultDataAccessException e) {
            usuario = null;
        }

        if (usuario == null) {
            jdbcTemplate.update(
                    "INSERT INTO convidados_pendentes (email) VALUES (LOWER(?)) ON CONFLICT (email) DO NOTHING",
                    emailNormalizado);
            emailService.sendConvidadoEmailAsync(emailNormalizado, null, linkCadastro(emailNormalizado));
            return false;
        }

        Long usuarioId = ((Number) usuario.get("id")).longValue();
        jdbcTemplate.update(
                "INSERT INTO usuarios_convidados (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        jdbcTemplate.update("DELETE FROM convidados_pendentes WHERE LOWER(email) = LOWER(?)", emailNormalizado);
        emailService.sendConvidadoEmailAsync(emailNormalizado, (String) usuario.get("username"), linkBase() + "/agente");
        return true;
    }

    // Chamado pelo UsuarioController assim que um cadastro é concluído.
    @Transactional
    public void promoverPendentes(Long usuarioId, String email) {
        if (usuarioId == null || email == null || email.isBlank()) {
            return;
        }
        int removidos = jdbcTemplate.update(
                "DELETE FROM convidados_pendentes WHERE LOWER(email) = LOWER(?)", email.trim());
        if (removidos > 0) {
            jdbcTemplate.update(
                    "INSERT INTO usuarios_convidados (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        }
    }

    @Transactional
    public void remover(Long usuarioId) {
        jdbcTemplate.update("DELETE FROM usuarios_convidados WHERE usuario_id = ?", usuarioId);
    }

    @Transactional
    public void removerPendente(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM convidados_pendentes WHERE LOWER(email) = LOWER(?)", email.trim());
    }

    private String linkBase() {
        return (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.replaceAll("/$", "")
                : "http://localhost:4200";
    }

    // O e-mail na URL trava o campo no /cadastro (ver CadastroComponent.emailTravado).
    private String linkCadastro(String email) {
        return linkBase() + "/cadastro?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }
}
