package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(Usuario user, String siteURL) throws MessagingException, UnsupportedEncodingException {
        String toAddress = user.getEmail();
        String fromAddress = "bicentraluft@gmail.com";
        String senderName = "BI Central";
        String subject = "Verifique seu cadastro";
        String content = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verifique seu Cadastro</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                    <tr>
                        <td style="padding: 20px 0;">
                            <table align="center" border="0" cellpadding="0" cellspacing="0" width="600" style="border-collapse: collapse; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);">
                                <tr>
                                    <td align="center" style="padding: 40px 0; background-color: #007bff; color: #ffffff; font-size: 24px; font-weight: bold; border-top-left-radius: 10px; border-top-right-radius: 10px;">
                                        Bem-vindo ao BI Central!
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 30px;">
                                        <h1 style="color: #333333; font-size: 22px;">Olá, [[name]]!</h1>
                                        <p style="color: #555555; font-size: 16px; line-height: 1.5;">
                                            Obrigado por se cadastrar. Por favor, clique no botão abaixo para verificar seu endereço de e-mail e ativar sua conta.
                                        </p>
                                        <table border="0" cellpadding="0" cellspacing="0" width="100%">
                                            <tr>
                                                <td align="center" style="padding: 20px 0;">
                                                    <a href="[[URL]]" target="_blank" style="background-color: #007bff; color: #ffffff; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold;">VERIFICAR E-MAIL</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="color: #555555; font-size: 16px; line-height: 1.5;">
                                            Se você não se cadastrou em nosso site, por favor, ignore este e-mail.
                                        </p>
                                        <p style="color: #555555; font-size: 16px; line-height: 1.5;">
                                            Atenciosamente,<br>
                                            Equipe BI Central
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding: 20px; font-size: 12px; color: #888888; border-bottom-left-radius: 10px; border-bottom-right-radius: 10px;">
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
        MimeMessageHelper helper = new MimeMessageHelper(message);

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);

        content = content.replace("[[name]]", user.getUsername());
        String verifyURL = siteURL + "/api/auth/verify?code=" + user.getVerificationToken();
        content = content.replace("[[URL]]", verifyURL);

        helper.setText(content, true);

        mailSender.send(message);
    }
}
