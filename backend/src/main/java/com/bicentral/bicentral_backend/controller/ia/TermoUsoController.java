package com.bicentral.bicentral_backend.controller.ia;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.TermoUsoService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/termo-uso")
public class TermoUsoController {

    private final TermoUsoService termoUsoService;
    private final UsuarioService usuarioService;

    public TermoUsoController(TermoUsoService termoUsoService, UsuarioService usuarioService) {
        this.termoUsoService = termoUsoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public Map<String, Object> consultar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return Map.of(
                "versao", TermoUsoService.VERSAO_ATUAL,
                "aceito", termoUsoService.aceitouVersaoAtual(usuario.getId()));
    }

    @PostMapping("/aceite")
    public Map<String, Object> aceitar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        termoUsoService.registrarAceite(usuario.getId());
        return Map.of("versao", TermoUsoService.VERSAO_ATUAL, "aceito", true);
    }
}
