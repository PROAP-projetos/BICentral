package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.AdminService;
import com.bicentral.bicentral_backend.service.AdminService.AdminSistemaDTO;
import com.bicentral.bicentral_backend.service.AdminService.AdminUsuarioRequestDTO;
import com.bicentral.bicentral_backend.service.AdminService.ConfiguracaoNotificacaoDTO;
import com.bicentral.bicentral_backend.service.AdminService.ConfiguracaoNotificacaoRequestDTO;
import com.bicentral.bicentral_backend.service.AdminService.GerenteDepartamentoDTO;
import com.bicentral.bicentral_backend.service.AdminService.GerenteDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.service.AdminService.UsuarioResumoDTO;
import com.bicentral.bicentral_backend.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final UsuarioService usuarioService;

    public AdminController(AdminService adminService, UsuarioService usuarioService) {
        this.adminService = adminService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/sou-admin")
    public ResponseEntity<Map<String, Boolean>> souAdmin(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(Map.of("admin", adminService.isAdmin(usuario.getId())));
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioResumoDTO>> listarUsuarios(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarUsuarios(usuario.getId()));
    }

    @GetMapping("/admins")
    public ResponseEntity<List<AdminSistemaDTO>> listarAdmins(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarAdmins(usuario.getId()));
    }

    @PostMapping("/admins")
    public ResponseEntity<Void> adicionarAdmin(
            @RequestBody AdminUsuarioRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.adicionarAdmin(usuario.getId(), request.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{usuarioId}")
    public ResponseEntity<Void> removerAdmin(
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.removerAdmin(usuario.getId(), usuarioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/gerentes")
    public ResponseEntity<List<GerenteDepartamentoDTO>> listarGerentes(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarGerentes(usuario.getId()));
    }

    @PostMapping("/gerentes")
    public ResponseEntity<GerenteDepartamentoDTO> adicionarGerente(
            @RequestBody GerenteDepartamentoRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.adicionarGerente(usuario.getId(), request));
    }

    @DeleteMapping("/gerentes/{id}")
    public ResponseEntity<Void> removerGerente(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.removerGerente(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/configuracoes-notificacao")
    public ResponseEntity<ConfiguracaoNotificacaoDTO> buscarConfiguracoesNotificacao(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.buscarConfiguracoesNotificacao(usuario.getId()));
    }

    @PutMapping("/configuracoes-notificacao")
    public ResponseEntity<ConfiguracaoNotificacaoDTO> salvarConfiguracoesNotificacao(
            @RequestBody ConfiguracaoNotificacaoRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.salvarConfiguracoesNotificacao(usuario.getId(), request));
    }

    private Usuario usuarioLogado(UserDetails userDetails) {
        return usuarioService.buscarPorEmail(userDetails.getUsername());
    }
}
