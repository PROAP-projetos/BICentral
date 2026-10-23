package com.bicentral.bicentral_backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Serve o robozinho do proIAp usado nos e-mails transacionais (EmailService/Brevo). Precisa ser
// uma URL pública de verdade: a API HTTP do Brevo não tem equivalente ao cid: do MIME
// multipart/related, e a alternativa (embutir a imagem como data URI direto no HTML) é bloqueada
// silenciosamente pela maioria dos clientes de e-mail — Gmail incluso.
@RestController
public class EmailAssetController {

    @GetMapping("/email/proiap-robo.gif")
    public ResponseEntity<Resource> roboProiap() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/gif"))
                .body(new ClassPathResource("email/proiap-robo.gif"));
    }
}
