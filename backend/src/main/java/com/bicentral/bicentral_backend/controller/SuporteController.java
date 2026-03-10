package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.SuporteRequestDTO;
import com.bicentral.bicentral_backend.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/suporte")
public class SuporteController {

    private final EmailService emailService;

    public SuporteController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> enviarContato(@Valid @RequestBody SuporteRequestDTO dto) {
        try {
            emailService.sendSupportEmail(dto.nome(), dto.email(), dto.assunto(), dto.mensagem());
            Map<String, String> response = new HashMap<>();
            response.put("mensagem", "Sua mensagem foi enviada ao suporte com sucesso.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("mensagem", "Não foi possível enviar sua mensagem agora. Tente novamente em instantes.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

