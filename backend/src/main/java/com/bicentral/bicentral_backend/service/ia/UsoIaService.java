package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.ia.TesterProiapDTO;
import com.bicentral.bicentral_backend.service.auth.EmailService;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UsoIaService {

    private static final Logger logger = LoggerFactory.getLogger(UsoIaService.class);

    private static final double TERRA_ENTRADA_POR_TOKEN = 2.00 / 1_000_000;
    private static final double TERRA_SAIDA_POR_TOKEN = 12.00 / 1_000_000;
    private static final double SOBRETAXA_LUNA_POR_TURNO = 0.0015;

    // Quem eram os testers do time antes de virar tabela — mantidos aqui só pra semear a
    // tabela na primeira subida (incl. no deploy), sem perder o teste em andamento.
    private static final List<Long> TESTERS_INICIAIS = List.of(10L);

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    public UsoIaService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS interacao_ia_log (
                id BIGSERIAL PRIMARY KEY,
                usuario_id BIGINT,
                pergunta TEXT NOT NULL,
                resposta_resumo TEXT,
                tokens_entrada INTEGER,
                tokens_saida INTEGER,
                custo_estimado NUMERIC(10,6) NOT NULL,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("ALTER TABLE interacao_ia_log ADD COLUMN IF NOT EXISTS feedback TEXT");
        // Liga cada linha à conversa (sessão) de onde veio — sem isso não dá pra reconstituir
        // quais perguntas fizeram parte da mesma conversa na hora de analisar.
        jdbcTemplate.execute("ALTER TABLE interacao_ia_log ADD COLUMN IF NOT EXISTS sessao_id TEXT");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS testers_proiap (
                usuario_id BIGINT PRIMARY KEY,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        for (Long id : TESTERS_INICIAIS) {
            jdbcTemplate.update(
                "INSERT INTO testers_proiap (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", id);
        }

        // E-mails adicionados como tester antes de terem conta no BICentral. Viram tester de
        // verdade (linha em testers_proiap) sozinhos assim que a pessoa se cadastra com esse
        // e-mail — ver promoverPendentesParaTester, chamado pelo UsuarioController no cadastro.
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS testers_proiap_pendentes (
                email TEXT PRIMARY KEY,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    public Long registrarUso(Long usuarioId, String sessaoId, String pergunta, String respostaResumo, TokenUsage tokenUsage) {
        int tokensEntrada = (tokenUsage != null && tokenUsage.inputTokenCount() != null) ? tokenUsage.inputTokenCount() : 0;
        int tokensSaida = (tokenUsage != null && tokenUsage.outputTokenCount() != null) ? tokenUsage.outputTokenCount() : 0;

        double custo = (tokensEntrada * TERRA_ENTRADA_POR_TOKEN)
                + (tokensSaida * TERRA_SAIDA_POR_TOKEN)
                + SOBRETAXA_LUNA_POR_TURNO;

        return jdbcTemplate.queryForObject("""
            INSERT INTO interacao_ia_log (usuario_id, sessao_id, pergunta, resposta_resumo, tokens_entrada, tokens_saida, custo_estimado)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class, usuarioId, sessaoId, truncar(pergunta), truncar(respostaResumo), tokensEntrada, tokensSaida, custo);
    }

    // Só atualiza se a interação for do próprio usuário logado — senão dá pra mandar
    // feedback pra conversa de outra pessoa só adivinhando o ID.
    public void salvarFeedback(Long interacaoId, String comentario, Long usuarioId) {
        int linhasAfetadas = jdbcTemplate.update(
                "UPDATE interacao_ia_log SET feedback = ? WHERE id = ? AND usuario_id = ?",
                truncar(comentario), interacaoId, usuarioId);
        if (linhasAfetadas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interação não encontrada.");
        }
    }

    // Cada tester tem seu próprio orçamento de US$ 1,00 — um tester gastão não trava os outros.
    public static final double LIMITE_DOLARES = 1.00;

    public double custoDoUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return 0.0;
        }
        Double total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(custo_estimado), 0) FROM interacao_ia_log WHERE usuario_id = ?",
                Double.class, usuarioId);
        return total != null ? total : 0.0;
    }

    public boolean deveBloquear(Long usuarioId) {
        if (!ehTester(usuarioId)) {
            return false;
        }
        return custoDoUsuario(usuarioId) >= LIMITE_DOLARES;
    }

    public boolean ehTester(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM testers_proiap WHERE usuario_id = ?", Integer.class, usuarioId);
        return total != null && total > 0;
    }

    // Usado no cadastro: quem já foi convidado como tester por e-mail pula a verificação de
    // e-mail (ver UsuarioService.cadastrar) — não faz sentido exigir esse passo extra de quem
    // está só testando o proIAp.
    public boolean emailTesterPendente(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM testers_proiap_pendentes WHERE LOWER(email) = LOWER(?)", Integer.class, email.trim());
        return total != null && total > 0;
    }

    public List<TesterProiapDTO> listarTesters() {
        return jdbcTemplate.query("""
            (SELECT t.usuario_id AS usuario_id, u.username AS nome, u.email AS email, t.criado_em AS criado_em,
                    COALESCE((SELECT SUM(l.custo_estimado) FROM interacao_ia_log l
                              WHERE l.usuario_id = t.usuario_id), 0)::numeric AS gasto,
                    false AS pendente
             FROM testers_proiap t
             LEFT JOIN usuario u ON u.id = t.usuario_id)
            UNION ALL
            (SELECT NULL::bigint AS usuario_id, NULL::text AS nome, p.email AS email, p.criado_em AS criado_em,
                    0::numeric AS gasto, true AS pendente
             FROM testers_proiap_pendentes p)
            ORDER BY pendente, nome NULLS LAST, email
            """, (rs, rowNum) -> new TesterProiapDTO(
                (Long) rs.getObject("usuario_id"),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getDouble("gasto"),
                LIMITE_DOLARES,
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getBoolean("pendente")
        ));
    }

    // Retorna true se virou tester confirmado na hora (já tinha conta), false se ficou
    // pendente aguardando a pessoa se cadastrar (ver promoverPendentesParaTester).
    @Transactional
    public boolean adicionarTester(String email) {
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
                    "INSERT INTO testers_proiap_pendentes (email) VALUES (LOWER(?)) ON CONFLICT (email) DO NOTHING",
                    emailNormalizado);
            enviarConviteTesterPendente(emailNormalizado);
            return false;
        }

        Long usuarioId = ((Number) usuario.get("id")).longValue();
        jdbcTemplate.update(
                "INSERT INTO testers_proiap (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        // Se ela tinha ficado pendente antes com esse e-mail (raro, mas possível), limpa.
        jdbcTemplate.update("DELETE FROM testers_proiap_pendentes WHERE LOWER(email) = LOWER(?)", emailNormalizado);
        enviarAvisoTesterConfirmado(emailNormalizado, (String) usuario.get("username"));
        return true;
    }

    // E-mails são "best effort": se o Brevo falhar, o tester já foi gravado no banco e não
    // deve ficar preso por causa disso — só loga o erro.
    private void enviarAvisoTesterConfirmado(String email, String nome) {
        try {
            emailService.sendTesterAddedEmail(email, nome != null && !nome.isBlank() ? nome : email);
        } catch (Exception e) {
            logger.error("Falha ao enviar e-mail de tester confirmado para {}", email, e);
        }
    }

    private void enviarConviteTesterPendente(String email) {
        try {
            String base = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                    ? frontendBaseUrl.replaceAll("/$", "")
                    : "http://localhost:4200";
            emailService.sendTesterInviteEmail(email, base + "/cadastro");
        } catch (Exception e) {
            logger.error("Falha ao enviar convite de tester pendente para {}", email, e);
        }
    }

    // Chamado pelo UsuarioController assim que um cadastro é concluído — promove um
    // tester pendente pra tester de verdade se o e-mail bater.
    @Transactional
    public void promoverPendentesParaTester(Long usuarioId, String email) {
        if (usuarioId == null || email == null || email.isBlank()) {
            return;
        }
        int removidos = jdbcTemplate.update(
                "DELETE FROM testers_proiap_pendentes WHERE LOWER(email) = LOWER(?)", email.trim());
        if (removidos > 0) {
            jdbcTemplate.update(
                    "INSERT INTO testers_proiap (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        }
    }

    @Transactional
    public void removerTester(Long usuarioId) {
        jdbcTemplate.update("DELETE FROM testers_proiap WHERE usuario_id = ?", usuarioId);
    }

    @Transactional
    public void removerTesterPendente(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM testers_proiap_pendentes WHERE LOWER(email) = LOWER(?)", email.trim());
    }

    public List<Map<String, Object>> listarInteracoes(int limite) {
        return jdbcTemplate.queryForList("""
            SELECT id, usuario_id, sessao_id, pergunta, resposta_resumo, feedback, tokens_entrada, tokens_saida, custo_estimado, criado_em
            FROM interacao_ia_log
            ORDER BY criado_em DESC
            LIMIT ?
            """, limite);
    }

    // Limite bem folgado — é só uma rede de segurança contra input patológico, não deve
    // cortar pergunta ou resposta reais (a coluna é TEXT, sem limite de banco).
    private String truncar(String texto) {
        if (texto == null) return null;
        return texto.length() > 20_000 ? texto.substring(0, 20_000) : texto;
    }
}
