package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;




//Envia o email de confirmação para o usuário, CUIDADO, A API É DO BREVO


@Service
public class EmailService {

    private static final String FROM_ADDRESS = "bicentraluft@gmail.com";
    private static final String SENDER_NAME = "BI Central";
    private static final DateTimeFormatter INVITE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(@NonNull Usuario user, @NonNull String siteURL) throws MessagingException, UnsupportedEncodingException {
        String toAddress = Objects.requireNonNull(user.getEmail(), "user email");
        String fromAddress = FROM_ADDRESS; // Lembre-se: Este email DEVE estar validado no Brevo
        String senderName = SENDER_NAME;
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


        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); // Habilitar UTF-8

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);

        content = content.replace("[[name]]", Objects.requireNonNull(user.getNomeExibicao(), "username"));
        String verifyURL = siteURL + "/api/usuarios/verify?code=" + Objects.requireNonNull(user.getVerificationToken(), "verification token");
        content = content.replace("[[URL]]", verifyURL);

        helper.setText(Objects.requireNonNull(content), true); // O 'true' é crucial para interpretar como HTML

        mailSender.send(message);
    }

    public void sendSupportEmail(
            @NonNull String nome,
            @NonNull String email,
            @NonNull String assunto,
            @NonNull String mensagem
    ) throws MessagingException, UnsupportedEncodingException {
        String fromAddress = FROM_ADDRESS;
        String senderName = "BI Central - Suporte";
        String toAddress = FROM_ADDRESS;

        String assuntoFinal = "[Suporte BICentral] " + assunto.trim();
        String conteudo = """
                Novo contato recebido pelo formulário de suporte.

                Nome: %s
                E-mail: %s
                Assunto: %s

                Mensagem:
                %s
                """.formatted(nome.trim(), email.trim(), assunto.trim(), mensagem.trim());

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setReplyTo(email.trim());
        helper.setSubject(assuntoFinal);
        helper.setText(conteudo, false);

        mailSender.send(message);
    }

    public void sendTeamInviteEmail(
            @NonNull Equipe equipe,
            @NonNull String email,
            @NonNull Role role,
            @NonNull String inviteUrl,
            @NonNull LocalDateTime expiraEm
    ) throws MessagingException, UnsupportedEncodingException {
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

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(FROM_ADDRESS, SENDER_NAME);
        helper.setTo(email);
        helper.setSubject(assunto);
        helper.setText(content, true);

        mailSender.send(message);
    }
}

