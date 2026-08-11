package com.bicentral.bicentral_backend.service.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftDTO;
import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftRequestDTO;
import com.bicentral.bicentral_backend.dto.uft.ResultadoTesteUftDTO;
import com.bicentral.bicentral_backend.dto.admin.AdminSistemaDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoDTO;
import com.bicentral.bicentral_backend.dto.admin.AdminUsuarioRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdminService {

    private static final long BOOTSTRAP_ADMIN_ID = 22L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admins_sistema", Integer.class);
        if (total == null || total == 0) {
            jdbcTemplate.update("""
                INSERT INTO admins_sistema (usuario_id)
                VALUES (?)
                ON CONFLICT (usuario_id) DO NOTHING
                """, BOOTSTRAP_ADMIN_ID);
        }
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

    public List<ConfiguracaoUftDTO> listarConfiguracoesUft(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        
        // Retorna a lista completa com as 3 configurações (TAREFAS, PAT, PDI)
        return jdbcTemplate.query("""
            SELECT id, tipo_api, url, ativo, ultima_execucao, ultimo_status, ultima_mensagem,
                   CASE WHEN token IS NOT NULL AND token != '' THEN true ELSE false END AS token_configurado
            FROM integracao_uft
            ORDER BY id
            """, (rs, rowNum) -> new ConfiguracaoUftDTO(
                rs.getInt("id"),
                rs.getString("tipo_api"),
                rs.getString("url"),
                rs.getBoolean("token_configurado"),
                rs.getBoolean("ativo"),
                rs.getObject("ultima_execucao", java.time.LocalDateTime.class),
                rs.getString("ultimo_status"),
                rs.getString("ultima_mensagem")
        ));
    }

    @Transactional
    public void salvarConfiguracaoUft(Long usuarioLogadoId, String tipoApi, ConfiguracaoUftRequestDTO request) {
        exigirAdmin(usuarioLogadoId);
        validarConfiguracaoUftRequest(request);

        // Agora ele faz o UPDATE filtrando pela coluna tipo_api (ex: WHERE tipo_api = 'TAREFAS')
        int atualizados;
        if (request.token() == null || request.token().isBlank()) {
            atualizados = jdbcTemplate.update("UPDATE integracao_uft SET url = ?, ativo = ? WHERE tipo_api = ?",
                request.url(), request.ativo(), tipoApi);
        } else {
            atualizados = jdbcTemplate.update("UPDATE integracao_uft SET url = ?, token = ?, ativo = ? WHERE tipo_api = ?",
                request.url(), request.token(), request.ativo(), tipoApi);
        }

        if (atualizados == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de API desconhecido: " + tipoApi);
        }
    }

    public ResultadoTesteUftDTO testarConexaoUft(Long usuarioLogadoId, String tipoApi, ConfiguracaoUftRequestDTO request) {
        exigirAdmin(usuarioLogadoId);

        if (request.url() == null || request.url().isBlank()) {
            return new ResultadoTesteUftDTO(false, "Informe a URL antes de testar.");
        }
        if (!request.url().matches("^https?://.+")) {
            return new ResultadoTesteUftDTO(false, "URL inválida (deve começar com http:// ou https://).");
        }

        String token = request.token();
        if (token == null || token.isBlank()) {
            token = buscarTokenSalvo(tipoApi);
            if (token == null || token.isBlank()) {
                return new ResultadoTesteUftDTO(false, "Informe um token ou salve um token antes de testar.");
            }
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ResultadoTesteUftDTO(false, "A API respondeu com HTTP " + response.statusCode() + ".");
            }
            if (response.body().trim().matches("(?s)\\{\\s*\"erro\"\\s*:.*\\}")) {
                return new ResultadoTesteUftDTO(false, "A API da UFT retornou um erro interno.");
            }
            return new ResultadoTesteUftDTO(true, "Conexão bem-sucedida.");
        } catch (HttpTimeoutException e) {
            return new ResultadoTesteUftDTO(false, "Tempo limite excedido ao tentar conectar.");
        } catch (Exception e) {
            return new ResultadoTesteUftDTO(false, "Falha ao conectar: " + e.getMessage());
        }
    }

    private String buscarTokenSalvo(String tipoApi) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT token FROM integracao_uft WHERE tipo_api = ?", String.class, tipoApi);
        } catch (Exception e) {
            return null;
        }
    }

    private void validarConfiguracaoUftRequest(ConfiguracaoUftRequestDTO request) {
        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a URL da API");
        }
        if (!request.url().matches("^https?://.+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL inválida (deve começar com http:// ou https://)");
        }
        if (request.ativo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe se a integração está ativa");
        }
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
}