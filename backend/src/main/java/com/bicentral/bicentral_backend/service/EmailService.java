package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(Usuario user, String siteURL) {
        String toAddress = user.getEmail();
        String fromAddress = "your-email@example.com";
        String senderName = "Your company name";
        String subject = "Please verify your registration";
        String content = "Dear [[name]],<br>"
                + "Please click the link below to verify your registration:<br>"
                + "<h3><a href=\"[[URL]]\" target=\"_self\">VERIFY</a></h3>"
                + "Thank you,<br>"
                + "Your company name.";

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject(subject);

        content = content.replace("[[name]]", user.getUsername());
        String verifyURL = siteURL + "/auth/verify?code=" + user.getVerificationToken();

        content = content.replace("[[URL]]", verifyURL);

        message.setText(content);

        mailSender.send(message);
    }
}
