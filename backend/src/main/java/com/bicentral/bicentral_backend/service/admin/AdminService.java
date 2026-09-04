package com.bicentral.bicentral_backend.service.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import com.bicentral.bicentral_backend.service.auth.EmailService;
import java.security.SecureRandom;

import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftDTO;
import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftRequestDTO;
import com.bicentral.bicentral_backend.dto.uft.ResultadoTesteUftDTO;
import com.bicentral.bicentral_backend.dto.admin.AdminSistemaDTO;
import com.bicentral.bicentral_backend.dto.admin.ClassificacaoDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoDTO;
import com.bicentral.bicentral_backend.dto.admin.AdminUsuarioRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResponsavelDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResponsavelRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResumoDTO;
import com.bicentral.bicentral_backend.dto.admin.ConviteAdminRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConviteAdminResponseDTO;
import com.bicentral.bicentral_backend.dto.admin.AceiteConviteAdminResponseDTO;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Base64;

@Service
public class AdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    @Value("${convite.admin.expiracao-horas:72}")
    private long expiracaoHoras;

    public AdminService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        garantirTabelaAdmins();
        garantirTabelaResponsaveis();
        garantirIndiceGerentes();
        garantirTabelaDepartamentoTipo();
        garantirTabelaConvitesAdmin();
    }

    private void garantirTabelaConvitesAdmin() {
        jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS convites_admin (
            id BIGSERIAL PRIMARY KEY,
            usuario_id BIGINT NOT NULL,
            token TEXT NOT NULL UNIQUE,
            status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
            criado_por BIGINT NOT NULL,
            criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            expira_em TIMESTAMPTZ NOT NULL,
            aceito_em TIMESTAMPTZ
        )
        """);
        // evita dois convites PENDENTE pro mesmo usuário (mesmo problema do Bug 4 de equipe)
        jdbcTemplate.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS convites_admin_usuario_pendente_key
        ON convites_admin (usuario_id)
        WHERE status = 'PENDENTE'
        """);
    }

    private static final long BOOTSTRAP_ADMIN_ID = 22L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Classificar o tipo (UA/UG) de um departamento estava amarrado a atribuir um gerente de
    // verdade em gerentes_departamento (usuario_id é obrigatório lá) — por isso 77 coordenações
    // de curso nunca foram classificadas, ninguém ia inventar um gerente falso só pra isso.
    // Essa tabela separa as duas coisas: aqui só se classifica o tipo, sem precisar de usuário.
    // IF NOT EXISTS também aparece em ConsultaAcoesTool (a view que lê essa tabela) — os dois
    // lados garantem a tabela porque a ordem de inicialização dos beans não é garantida.
    private void garantirTabelaDepartamentoTipo() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS departamento_tipo (
                departamento TEXT PRIMARY KEY,
                tipo_unidade VARCHAR(2) NOT NULL,
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    @Transactional
    public void classificarDepartamento(Long usuarioLogadoId, ClassificacaoDepartamentoRequestDTO request) {
        exigirAdmin(usuarioLogadoId);
        if (request.departamento() == null || request.departamento().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o departamento");
        }
        if (!"UA".equals(request.tipoUnidade()) && !"UG".equals(request.tipoUnidade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de unidade deve ser UA ou UG");
        }
        jdbcTemplate.update("""
            INSERT INTO departamento_tipo (departamento, tipo_unidade)
            VALUES (?, ?)
            ON CONFLICT (departamento) DO UPDATE SET tipo_unidade = EXCLUDED.tipo_unidade, atualizado_em = NOW()
            """, request.departamento().trim(), request.tipoUnidade());
    }

    // gerentes_departamento foi criada direto no banco (não tem CREATE TABLE aqui no código),
    // e nunca teve proteção contra duplicata — foi exatamente essa tabela que duplicou de
    // verdade quando um cadastro foi enviado duas vezes seguidas. Um usuário pode legitimamente
    // gerenciar mais de um departamento, então a chave única é o PAR (usuario_id, departamento),
    // não usuario_id sozinho.
    private void garantirIndiceGerentes() {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS gerentes_departamento_usuario_depto_key ON gerentes_departamento (usuario_id, departamento)");
        // tipo_unidade aqui é legado — a classificação de tipo agora mora em departamento_tipo,
        // sem depender de gerente nenhum (ver garantirTabelaDepartamentoTipo). Deixar de exigir
        // NOT NULL permite adicionar um gerente sem precisar (mal) inventar um tipo pra ele.
        jdbcTemplate.execute("ALTER TABLE gerentes_departamento ALTER COLUMN tipo_unidade DROP NOT NULL");
    }

    private void garantirTabelaResponsaveis() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS usuario_responsavel (
                id BIGSERIAL PRIMARY KEY,
                usuario_id BIGINT NOT NULL,
                nome_responsavel TEXT NOT NULL
            )
            """);
        // A tabela já existia em produção sem "id" nem "created_at" (só usuario_id e
        // nome_responsavel) — o CREATE TABLE acima foi ignorado nela, então garante
        // essas colunas separadamente, direto com ALTER, que funciona mesmo já existindo.
        jdbcTemplate.execute("ALTER TABLE usuario_responsavel ADD COLUMN IF NOT EXISTS id BIGSERIAL");
        jdbcTemplate.execute("ALTER TABLE usuario_responsavel ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        // Sem isso, dois cliques seguidos em "adicionar responsável" (ou duas requisições quase
        // simultâneas) podem criar duas linhas pro mesmo usuário — e buscarMinhasTarefas() espera
        // exatamente uma linha por usuario_id, quebrando "minhas tarefas" pra essa pessoa. Índice
        // único (não constraint) porque Postgres não tem "ADD CONSTRAINT IF NOT EXISTS", mas index
        // único tem o IF NOT EXISTS e serve pro mesmo propósito, inclusive pro ON CONFLICT abaixo.
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS usuario_responsavel_usuario_id_key ON usuario_responsavel (usuario_id)");
    }

    private void garantirTabelaAdmins() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS admins_sistema (
                usuario_id BIGINT PRIMARY KEY
            )
            """);

        // Sempre garante o admin de bootstrap, não só quando a tabela está totalmente vazia — do
        // jeito que era antes. Só rodar quando vazia mudava o comportamento sem isso ter sido
        // combinado: se o admin de bootstrap fosse removido e outro colocado no lugar, ele não
        // voltava mais sozinho a cada restart. ON CONFLICT DO NOTHING já é idempotente por si só.
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

    @Transactional
    public ConviteAdminResponseDTO enviarConviteAdmin(Long usuarioLogadoId, ConviteAdminRequestDTO request, String siteUrl) {
        exigirAdmin(usuarioLogadoId);
        validarUsuarioExistente(request.usuarioId());

        if (isAdmin(request.usuarioId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuario ja e administrador.");
        }

        String token = gerarTokenSeguro();
        OffsetDateTime expiraEm = OffsetDateTime.now().plusHours(expiracaoHoras);

        // cancela convite pendente anterior pro mesmo usuário antes de criar um novo
        jdbcTemplate.update("DELETE FROM convites_admin WHERE usuario_id = ? AND status = 'PENDENTE'", request.usuarioId());

        Long id = jdbcTemplate.queryForObject("""
        INSERT INTO convites_admin (usuario_id, token, criado_por, expira_em)
        VALUES (?, ?, ?, ?)
        RETURNING id
        """, Long.class, request.usuarioId(), token, usuarioLogadoId, expiraEm);

        String nomeUsuario = jdbcTemplate.queryForObject(
                "SELECT username FROM usuario WHERE id = ?", String.class, request.usuarioId());
        String emailUsuario = jdbcTemplate.queryForObject(
                "SELECT email FROM usuario WHERE id = ?", String.class, request.usuarioId());

        String baseUrl = frontendBaseUrl != null && !frontendBaseUrl.isBlank() ? frontendBaseUrl.trim() : siteUrl;
        String link = baseUrl.replaceAll("/$", "") + "/aceitar-convite-admin?token=" + token;

        // ajuste o nome do método conforme o que existir de fato no seu EmailService
        emailService.sendAdminInviteEmail(emailUsuario, link, expiraEm);

        return new ConviteAdminResponseDTO(id, request.usuarioId(), nomeUsuario, "PENDENTE", expiraEm);
    }

    @Transactional
    public AceiteConviteAdminResponseDTO aceitarConviteAdmin(String token) {
        var convite = jdbcTemplate.queryForMap(
                "SELECT usuario_id, status, expira_em FROM convites_admin WHERE token = ?", token);

        if (convite == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite invalido.");
        }
        String status = (String) convite.get("status");
        OffsetDateTime expiraEm = (OffsetDateTime) convite.get("expira_em");
        Long usuarioId = (Long) convite.get("usuario_id");

        if ("ACEITO".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este convite ja foi utilizado.");
        }
        if (expiraEm.isBefore(OffsetDateTime.now())) {
            jdbcTemplate.update("UPDATE convites_admin SET status = 'EXPIRADO' WHERE token = ?", token);
            throw new ResponseStatusException(HttpStatus.GONE, "Este convite expirou.");
        }

        jdbcTemplate.update("""
        INSERT INTO admins_sistema (usuario_id)
        VALUES (?)
        ON CONFLICT (usuario_id) DO NOTHING
        """, usuarioId);

        jdbcTemplate.update(
                "UPDATE convites_admin SET status = 'ACEITO', aceito_em = NOW() WHERE token = ?", token);

        return new AceiteConviteAdminResponseDTO("Convite aceito com sucesso. Voce agora e administrador.");
    }

    private String gerarTokenSeguro() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

        // ON CONFLICT em vez de INSERT simples: clique duplo/requisição repetida atualiza o
        // vínculo existente em vez de criar uma segunda linha pro mesmo usuário+departamento.
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO gerentes_departamento (usuario_id, departamento, tipo_unidade)
            VALUES (?, ?, ?)
            ON CONFLICT (usuario_id, departamento) DO UPDATE SET tipo_unidade = EXCLUDED.tipo_unidade
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

    public List<UsuarioResponsavelDTO> listarResponsaveis(Long usuarioLogadoId) {
        exigirAdmin(usuarioLogadoId);
        return jdbcTemplate.query("""
            SELECT ur.id, ur.usuario_id, u.username AS usuario_nome, u.email AS usuario_email,
                   ur.nome_responsavel, ur.created_at
            FROM usuario_responsavel ur
            LEFT JOIN usuario u ON u.id = ur.usuario_id
            ORDER BY ur.nome_responsavel
            """, (rs, rowNum) -> new UsuarioResponsavelDTO(
                rs.getLong("id"),
                rs.getLong("usuario_id"),
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("nome_responsavel"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    @Transactional
    public UsuarioResponsavelDTO adicionarResponsavel(Long usuarioLogadoId, UsuarioResponsavelRequestDTO request) {
        exigirAdmin(usuarioLogadoId);
        validarUsuarioExistente(request.usuarioId());
        if (request.nomeResponsavel() == null || request.nomeResponsavel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do responsavel");
        }

        // Cada usuario so pode ter um responsavel vinculado; um novo vinculo substitui o anterior.
        // Upsert atômico (em vez de DELETE + INSERT em dois passos) pra não deixar janela de corrida
        // em cliques duplos/requisições simultâneas — o índice único acima é o que garante isso.
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO usuario_responsavel (usuario_id, nome_responsavel)
            VALUES (?, ?)
            ON CONFLICT (usuario_id) DO UPDATE SET nome_responsavel = EXCLUDED.nome_responsavel
            RETURNING id
            """, Long.class, request.usuarioId(), request.nomeResponsavel().trim());

        return buscarResponsavelPorId(id);
    }

    @Transactional
    public void removerResponsavel(Long usuarioLogadoId, Long id) {
        exigirAdmin(usuarioLogadoId);
        int removidos = jdbcTemplate.update("DELETE FROM usuario_responsavel WHERE id = ?", id);
        if (removidos == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculo de responsavel nao encontrado");
        }
    }

    public List<String> buscarResponsaveisPat(Long usuarioLogadoId, String busca) {
        exigirAdmin(usuarioLogadoId);
        if (busca == null || busca.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
            SELECT DISTINCT dados_completos->>'Responsável' AS nome
            FROM pat_tarefas
            WHERE dados_completos->>'Responsável' ILIKE ?
            ORDER BY nome
            LIMIT 15
            """, String.class, "%" + busca.trim() + "%");
    }

    private UsuarioResponsavelDTO buscarResponsavelPorId(Long id) {
        return jdbcTemplate.queryForObject("""
            SELECT ur.id, ur.usuario_id, u.username AS usuario_nome, u.email AS usuario_email,
                   ur.nome_responsavel, ur.created_at
            FROM usuario_responsavel ur
            LEFT JOIN usuario u ON u.id = ur.usuario_id
            WHERE ur.id = ?
            """, (rs, rowNum) -> new UsuarioResponsavelDTO(
                rs.getLong("id"),
                rs.getLong("usuario_id"),
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("nome_responsavel"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), id);
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
        // tipoUnidade agora é opcional aqui — quem classifica o tipo é /departamentos/classificar.
        String tipo = request.tipoUnidade();
        if (tipo != null && !tipo.isBlank() && !"UA".equals(tipo) && !"UG".equals(tipo)) {
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