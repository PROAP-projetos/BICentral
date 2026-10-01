package com.bicentral.bicentral_backend.controller.equipe;

import com.bicentral.bicentral_backend.dto.equipe.AceiteConviteRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.AceiteConviteResponseDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.equipe.ConviteEquipeService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/convites")
public class ConviteController {

    private final ConviteEquipeService conviteEquipeService;
    private final UsuarioService usuarioService;

    public ConviteController(ConviteEquipeService conviteEquipeService, UsuarioService usuarioService) {
        this.conviteEquipeService = conviteEquipeService;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/aceitar")
    public ResponseEntity<AceiteConviteResponseDTO> aceitarConvite(@Valid @RequestBody AceiteConviteRequestDTO dto, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        return ResponseEntity.ok(conviteEquipeService.aceitarConvite(dto.token(), usuarioLogado));
    }
}
