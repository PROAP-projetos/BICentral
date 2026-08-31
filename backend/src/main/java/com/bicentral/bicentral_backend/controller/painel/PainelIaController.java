package com.bicentral.bicentral_backend.controller.painel;

import com.bicentral.bicentral_backend.dto.painel.PainelIaDTO;
import com.bicentral.bicentral_backend.dto.painel.SalvarPainelIaRequestDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.painel.PainelIaService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Painéis gerados pela IA no chat e salvos pelo usuário — separado do PainelController (que é
// só link de embed do Power BI). Um é gráfico de verdade, o outro é um iframe; não faz sentido
// forçar os dois na mesma tabela/modelo.
@RestController
@RequestMapping("/api/paineis-ia")
public class PainelIaController {

    private final PainelIaService painelIaService;
    private final UsuarioService usuarioService;

    public PainelIaController(PainelIaService painelIaService, UsuarioService usuarioService) {
        this.painelIaService = painelIaService;
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<PainelIaDTO> salvar(
            @RequestBody SalvarPainelIaRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return ResponseEntity.ok(painelIaService.salvar(usuario.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<PainelIaDTO>> listar(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return ResponseEntity.ok(painelIaService.listarPorUsuario(usuario.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        painelIaService.excluir(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
