package com.bicentral.bicentral_backend.service.auth;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Envia e-mails transacionais pela API HTTP do Brevo (https://api.brevo.com/v3/smtp/email),
// não mais por SMTP puro. O Render bloqueia silenciosamente a porta 587 de saída — a conexão
// nem chega a autenticar, trava no connect() até estourar timeout (ver commit "envio de convite
// tester quebrou" e o log real: SocketTimeoutException em smtp-relay.brevo.com:587). A API roda
// em HTTPS (443), porta que nenhum provedor de hospedagem bloqueia.
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private static final String FROM_ADDRESS = "bicentraluft@gmail.com"; // precisa estar validado no Brevo
    private static final String SENDER_NAME = "BI Central";
    private static final DateTimeFormatter INVITE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final String GUIA_TESTER_PROIAP_RESOURCE = "/documentos/guia-proiap.pdf";
    private static final String ROBO_PROIAP_RESOURCE = "/email/proiap-robo.gif";

    private final RestClient restClient;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${app.backend-base-url:http://localhost:8080}")
    private String backendBaseUrl;

    public EmailService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.brevo.com/v3/smtp/email")
                .build();
    }

    private void enviarEmail(String toAddress, String toName, String assunto,
            String htmlContent, String textContent, String replyToEmail, List<Map<String, String>> anexos) {
        Map<String, String> destinatario = new LinkedHashMap<>();
        destinatario.put("email", toAddress);
        if (toName != null) destinatario.put("name", toName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", SENDER_NAME, "email", FROM_ADDRESS));
        body.put("to", List.of(destinatario));
        body.put("subject", assunto);
        if (htmlContent != null) body.put("htmlContent", htmlContent);
        if (textContent != null) body.put("textContent", textContent);
        if (replyToEmail != null) body.put("replyTo", Map.of("email", replyToEmail));
        if (anexos != null && !anexos.isEmpty()) body.put("attachment", anexos);

        restClient.post()
                .header("api-key", brevoApiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // Anexo é "best effort": se o guia não estiver empacotado por algum motivo, o e-mail
    // ainda sai sem ele em vez de falhar o envio inteiro.
    private List<Map<String, String>> guiaProiapAnexo() {
        try (InputStream is = getClass().getResourceAsStream(GUIA_TESTER_PROIAP_RESOURCE)) {
            if (is == null) {
                logger.warn("Guia do proIAp não encontrado em {}", GUIA_TESTER_PROIAP_RESOURCE);
                return List.of();
            }
            String base64 = Base64.getEncoder().encodeToString(is.readAllBytes());
            return List.of(Map.of("name", "Guia do proIAp.pdf", "content", base64));
        } catch (IOException e) {
            logger.error("Falha ao ler o guia do proIAp", e);
            return List.of();
        }
    }

    // URL pública de verdade (ver EmailAssetController) — data URI embutido no HTML foi
    // tentado antes e é bloqueado silenciosamente pela maioria dos clientes de e-mail (Gmail
    // incluso), aparecendo como imagem quebrada.
    private String roboProiapUrl() {
        return backendBaseUrl + ROBO_PROIAP_RESOURCE;
    }

    public void sendVerificationEmail(Usuario user, String siteURL) {
        String toAddress = Objects.requireNonNull(user.getEmail(), "user email");
        String subject = "Verifique seu cadastro";

        String content = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verifique seu Cadastro</title>
                <style>
                    /* Estilos para clientes que não suportam <style> no head serão aplicados inline */
                    body {
                        margin: 0;
                        padding: 0;
                        font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;
                        background-color: #f4f7f6;
                    }
                    .container {
                        width: 100%;
                        max-width: 600px;
                        margin: 0 auto;
                        background-color: #ffffff;
                        border-radius: 8px;
                        overflow: hidden; /* Garante que o radius funcione no header */
                    }
                    .content {
                        padding: 30px 40px;
                    }
                    .header {
                        padding: 40px;
                        text-align: center;
                        background-color: #f9f9f9;
                        border-bottom: 1px solid #eeeeee;
                    }
                    .header h1 {
                        margin: 0;
                        color: #333333;
                        font-size: 24px;
                    }
                    .button {
                        display: inline-block;
                        padding: 14px 28px;
                        background-color: #007bff; /* Cor do botão principal */
                        color: #ffffff;
                        text-decoration: none;
                        font-weight: bold;
                        border-radius: 5px;
                        font-size: 16px;
                    }
                    .footer {
                        padding: 30px 40px;
                        text-align: center;
                        font-size: 12px;
                        color: #aaaaaa;
                    }
                    p {
                        font-size: 16px;
                        line-height: 1.6;
                        color: #555555;
                    }
                </style>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif; background-color: #f4f7f6;">
                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                    <tr>
                        <td style="padding: 20px 0;">
                            <table class="container" align="center" border="0" cellpadding="0" cellspacing="0" width="600" style="width: 100%; max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden;">

                                <tr>
                                    <td class="header" style="padding: 40px; text-align: center; background-color: #f9f9f9; border-bottom: 1px solid #eeeeee;">
                                        <h1 style="margin: 0; color: #333333; font-size: 24px;">BI Central</h1>
                                    </td>
                                </tr>

                                <tr>
                                    <td class="content" style="padding: 30px 40px;">
                                        <h2 style="color: #333333; font-size: 22px; margin-top: 0;">Olá, [[name]]!</h2>
                                        <p style="font-size: 16px; line-height: 1.6; color: #555555;">
                                            Obrigado por se cadastrar. Por favor, clique no botão abaixo para verificar seu endereço de e-mail e ativar sua conta.
                                        </p>

                                        <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                            <tr>
                                                <td align="center" style="padding: 20px 0;">
                                                    <a href="[[URL]]" target="_blank" class="button" style="display: inline-block; padding: 14px 28px; background-color: #007bff; color: #ffffff; text-decoration: none; font-weight: bold; border-radius: 5px; font-size: 16px;">
                                                        VERIFICAR E-MAIL
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="font-size: 16px; line-height: 1.6; color: #555555;">
                                            Se você não se cadastrou, por favor, ignore este e-mail.
                                        </p>
                                        <p style="font-size: 16px; line-height: 1.6; color: #555555;">
                                            Atenciosamente,<br>
                                            Equipe BI Central
                                        </p>
                                    </td>
                                </tr>

                                <tr>
                                    <td class="footer" style="padding: 30px 40px; text-align: center; font-size: 12px; color: #aaaaaa; border-top: 1px solid #eeeeee;">
                                        &copy; 2025 BI Central. Todos os direitos reservados.
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """;

        content = content.replace("[[name]]", Objects.requireNonNull(user.getNomeExibicao(), "username"));
        String verifyURL = siteURL + "/api/usuarios/verify?code=" + Objects.requireNonNull(user.getVerificationToken(), "verification token");
        content = content.replace("[[URL]]", verifyURL);

        enviarEmail(toAddress, null, subject, content, null, null, null);
    }

    public void sendSupportEmail(String nome, String email, String assunto, String mensagem) {
        String assuntoFinal = "[Suporte BICentral] " + assunto.trim();
        String conteudo = """
                Novo contato recebido pelo formulário de suporte.

                Nome: %s
                E-mail: %s
                Assunto: %s

                Mensagem:
                %s
                """.formatted(nome.trim(), email.trim(), assunto.trim(), mensagem.trim());

        enviarEmail(FROM_ADDRESS, null, assuntoFinal, null, conteudo, email.trim(), null);
    }

    public void sendTeamInviteEmail(Equipe equipe, String email, Role role, String inviteUrl, LocalDateTime expiraEm) {
        String assunto = "Convite para a equipe " + equipe.getNome();
        String expiraEmFormatado = expiraEm.format(INVITE_DATE_FORMATTER);

        String content = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Convite para equipe</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f7fa;font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;color:#1a1a1a;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%">
                        <tr>
                            <td style="padding:24px 12px;">
                                <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width:620px;background:#ffffff;border:1px solid rgba(0,74,128,0.08);border-radius:16px;overflow:hidden;">
                                    <tr>
                                        <td style="padding:28px 32px;background:#004a80;color:#ffffff;">
                                            <div style="font-size:24px;font-weight:700;letter-spacing:0.2px;">BICentral</div>
                                            <div style="margin-top:8px;font-size:14px;opacity:0.92;">Convite para acesso a equipe</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px;">
                                            <div style="display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(0,74,128,0.08);color:#004a80;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.4px;">
                                                Convite pendente
                                            </div>
                                            <h1 style="margin:18px 0 12px;font-size:28px;line-height:1.2;color:#113956;">Você foi convidado para a equipe %s</h1>
                                            <p style="margin:0 0 12px;font-size:16px;line-height:1.65;color:#3b556b;">
                                                O BICentral recebeu uma solicitação para adicionar o e-mail <strong>%s</strong> à equipe <strong>%s</strong> com o papel <strong>%s</strong>.
                                            </p>
                                            <p style="margin:0 0 24px;font-size:16px;line-height:1.65;color:#3b556b;">
                                                Para concluir, aceite o convite pelo botão abaixo. Este link expira em <strong>%s</strong>.
                                            </p>
                                            <table border="0" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td>
                                                        <a href="%s" target="_blank" style="display:inline-block;padding:14px 24px;background:#004a80;color:#ffffff;text-decoration:none;font-weight:700;border-radius:10px;">
                                                            Aceitar convite
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <div style="margin-top:24px;padding:16px 18px;border-radius:12px;background:#f8fbff;border:1px solid rgba(0,74,128,0.08);">
                                                <div style="font-size:13px;font-weight:700;color:#004a80;margin-bottom:8px;">Dica</div>
                                                <div style="font-size:14px;line-height:1.55;color:#486174;">
                                                    Se você ainda não possui cadastro no BICentral com este e-mail, conclua seu cadastro antes de aceitar o convite.
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:20px 32px;border-top:1px solid #e8eef5;font-size:12px;color:#7b8a97;">
                                            Se você não esperava este convite, ignore este e-mail.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(equipe.getNome(), email, equipe.getNome(), role.name(), expiraEmFormatado, inviteUrl);

        enviarEmail(email, null, assunto, content, null, null, null);
    }

    public void sendTesterAddedEmail(String toAddress, String nome) {
        String assunto = "Você agora é tester do proIAp";
        String content = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Você agora é tester do proIAp</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f7fa;font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;color:#1a1a1a;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%">
                        <tr>
                            <td style="padding:24px 12px;">
                                <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width:620px;background:#ffffff;border:1px solid rgba(0,74,128,0.08);border-radius:16px;overflow:hidden;">
                                    <tr>
                                        <td style="padding:28px 32px;background:#004a80;color:#ffffff;">
                                            <div style="font-size:24px;font-weight:700;letter-spacing:0.2px;">BICentral</div>
                                            <div style="margin-top:8px;font-size:14px;opacity:0.92;">proIAp — agente de IA</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 32px 0;text-align:center;background:#ffffff;">
                                            <img src="%s" width="88" height="88" alt="proIAp" style="display:block;margin:0 auto;border:0;outline:none;" />
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px;">
                                            <h1 style="margin:0 0 12px;font-size:26px;line-height:1.2;color:#113956;">Olá, %s!</h1>
                                            <p style="margin:0 0 12px;font-size:16px;line-height:1.65;color:#3b556b;">
                                                Você foi adicionado(a) como <strong>tester do proIAp</strong>, o agente de IA do BICentral. Ele lê os dados reais da PROAP — PDI, PAT, tarefas e desempenho por departamento — e responde na hora, em texto ou em gráfico.
                                            </p>
                                            <p style="margin:0;font-size:16px;line-height:1.65;color:#3b556b;">
                                                É só entrar no BICentral e clicar em "Pergunte ao agente". Anexamos um guia rápido em PDF com o que ele sabe fazer e exemplos de perguntas pra começar.
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:20px 32px;border-top:1px solid #e8eef5;font-size:12px;color:#7b8a97;">
                                            Se você não esperava este e-mail, ignore-o.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(roboProiapUrl(), nome);

        enviarEmail(toAddress, null, assunto, content, null, null, guiaProiapAnexo());
    }

    // Assíncrono porque quem chama (UsoIaService.adicionarTester) responde ao admin na hora —
    // o envio do e-mail não pode segurar a requisição.
    @Async
    public void sendTesterAddedEmailAsync(String toAddress, String nome) {
        try {
            sendTesterAddedEmail(toAddress, nome);
        } catch (Exception e) {
            logger.error("Falha ao enviar e-mail de tester confirmado para {}", toAddress, e);
        }
    }

    // Mesmo motivo do sendTesterAddedEmailAsync acima.
    @Async
    public void sendTesterInviteEmailAsync(String toAddress, String cadastroUrl) {
        try {
            sendTesterInviteEmail(toAddress, cadastroUrl);
        } catch (Exception e) {
            logger.error("Falha ao enviar convite de tester pendente para {}", toAddress, e);
        }
    }

    public void sendTesterInviteEmail(String toAddress, String cadastroUrl) {
        String assunto = "Convite para testar o proIAp";
        String content = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Convite para testar o proIAp</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f7fa;font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;color:#1a1a1a;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%">
                        <tr>
                            <td style="padding:24px 12px;">
                                <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width:620px;background:#ffffff;border:1px solid rgba(0,74,128,0.08);border-radius:16px;overflow:hidden;">
                                    <tr>
                                        <td style="padding:28px 32px;background:#004a80;color:#ffffff;">
                                            <div style="font-size:24px;font-weight:700;letter-spacing:0.2px;">BICentral</div>
                                            <div style="margin-top:8px;font-size:14px;opacity:0.92;">proIAp — agente de IA</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 32px 0;text-align:center;background:#ffffff;">
                                            <img src="%s" width="88" height="88" alt="proIAp" style="display:block;margin:0 auto;border:0;outline:none;" />
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px;">
                                            <div style="display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(0,74,128,0.08);color:#004a80;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.4px;">
                                                Convite pendente
                                            </div>
                                            <h1 style="margin:18px 0 12px;font-size:28px;line-height:1.2;color:#113956;">Você foi convidado(a) para testar o proIAp</h1>
                                            <p style="margin:0 0 12px;font-size:16px;line-height:1.65;color:#3b556b;">
                                                O proIAp é o agente de IA do BICentral. Ele lê os dados reais da PROAP — PDI, PAT, tarefas e desempenho por departamento — e responde na hora, em texto ou em gráfico.
                                            </p>
                                            <p style="margin:0 0 24px;font-size:16px;line-height:1.65;color:#3b556b;">
                                                Você ainda não tem uma conta no BICentral com este e-mail. Cadastre-se abaixo e você vira tester automaticamente assim que concluir o cadastro. Anexamos também um guia rápido em PDF com exemplos de perguntas pra você começar.
                                            </p>
                                            <table border="0" cellpadding="0" cellspacing="0">
                                                <tr>
                                                    <td>
                                                        <a href="%s" target="_blank" style="display:inline-block;padding:14px 24px;background:#004a80;color:#ffffff;text-decoration:none;font-weight:700;border-radius:10px;">
                                                            Criar minha conta
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:20px 32px;border-top:1px solid #e8eef5;font-size:12px;color:#7b8a97;">
                                            Se você não esperava este e-mail, ignore-o.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(roboProiapUrl(), cadastroUrl);

        enviarEmail(toAddress, null, assunto, content, null, null, guiaProiapAnexo());
    }
}
