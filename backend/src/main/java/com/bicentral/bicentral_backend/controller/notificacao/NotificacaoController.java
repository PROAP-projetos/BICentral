package com.bicentral.bicentral_backend.controller.notificacao;

import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.notificacao.NotificacaoService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/proiap")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final UsuarioService usuarioService;

    public NotificacaoController(NotificacaoService notificacaoService, UsuarioService usuarioService) {
        this.notificacaoService = notificacaoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/notificacoes")
    public ResponseEntity<List<NotificacaoDTO>> minhasNotificacoes(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return ResponseEntity.ok(notificacaoService.gerarNotificacoes(usuario.getId()));
    }
}
