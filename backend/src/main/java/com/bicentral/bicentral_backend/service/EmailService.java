package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;




//Envia o email de confirmação para o usuário, CUIDADO, A API É DO BREVO


@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(Usuario user, String siteURL) throws MessagingException, UnsupportedEncodingException {
        String toAddress = user.getEmail();
        String fromAddress = "bicentraluft@gmail.com"; // Lembre-se: Este email DEVE estar validado no Brevo
        String senderName = "BI Central";
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

        content = content.replace("[[name]]", user.getUsername());
        String verifyURL = siteURL + "/api/auth/verify?code=" + user.getVerificationToken();
        content = content.replace("[[URL]]", verifyURL);

        helper.setText(content, true); // O 'true' é crucial para interpretar como HTML

        mailSender.send(message);
    }
}