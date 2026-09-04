package com.bicentral.bicentral_backend.controller.admin;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.dto.admin.AdminSistemaDTO;
import com.bicentral.bicentral_backend.dto.admin.ClassificacaoDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoDTO;
import com.bicentral.bicentral_backend.dto.admin.AdminUsuarioRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConfiguracaoNotificacaoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoDTO;
import com.bicentral.bicentral_backend.dto.admin.GerenteDepartamentoRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResponsavelDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResponsavelRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.UsuarioResumoDTO;
import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftDTO;
import com.bicentral.bicentral_backend.dto.uft.ConfiguracaoUftRequestDTO;
import com.bicentral.bicentral_backend.dto.uft.ResultadoTesteUftDTO;
import com.bicentral.bicentral_backend.dto.admin.ConviteAdminRequestDTO;
import com.bicentral.bicentral_backend.dto.admin.ConviteAdminResponseDTO;
import com.bicentral.bicentral_backend.dto.admin.AceiteConviteAdminResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;

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
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.adicionarAdmin(usuario.getId(), request.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{usuarioId}")
    public ResponseEntity<Void> removerAdmin(
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.removerAdmin(usuario.getId(), usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/convites")
    public ResponseEntity<ConviteAdminResponseDTO> enviarConviteAdmin(
            @RequestBody ConviteAdminRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.enviarConviteAdmin(usuario.getId(), request, getSiteURL(httpRequest)));
    }

    @PostMapping("/convites/aceitar")
    public ResponseEntity<AceiteConviteAdminResponseDTO> aceitarConviteAdmin(@RequestParam String token) {
        return ResponseEntity.ok(adminService.aceitarConviteAdmin(token));
    }

    private String getSiteURL(HttpServletRequest request) {
        String siteURL = request.getRequestURL().toString();
        return siteURL.replace(request.getServletPath(), "");
    }

    @GetMapping("/gerentes")
    public ResponseEntity<List<GerenteDepartamentoDTO>> listarGerentes(
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarGerentes(usuario.getId()));
    }

    @PostMapping("/gerentes")
    public ResponseEntity<GerenteDepartamentoDTO> adicionarGerente(
            @RequestBody GerenteDepartamentoRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.adicionarGerente(usuario.getId(), request));
    }

    @PostMapping("/departamentos/classificar")
    public ResponseEntity<Void> classificarDepartamento(
            @RequestBody ClassificacaoDepartamentoRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.classificarDepartamento(usuario.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/gerentes/{id}")
    public ResponseEntity<Void> removerGerente(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.removerGerente(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/responsaveis")
    public ResponseEntity<List<UsuarioResponsavelDTO>> listarResponsaveis(
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarResponsaveis(usuario.getId()));
    }

    @PostMapping("/responsaveis")
    public ResponseEntity<UsuarioResponsavelDTO> adicionarResponsavel(
            @RequestBody UsuarioResponsavelRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.adicionarResponsavel(usuario.getId(), request));
    }

    @DeleteMapping("/responsaveis/{id}")
    public ResponseEntity<Void> removerResponsavel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.removerResponsavel(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/responsaveis-pat")
    public ResponseEntity<List<String>> buscarResponsaveisPat(
            @RequestParam String busca,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.buscarResponsaveisPat(usuario.getId(), busca));
    }

    @GetMapping("/configuracoes-notificacao")
    public ResponseEntity<ConfiguracaoNotificacaoDTO> buscarConfiguracoesNotificacao(
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.buscarConfiguracoesNotificacao(usuario.getId()));
    }

    @PutMapping("/configuracoes-notificacao")
    public ResponseEntity<ConfiguracaoNotificacaoDTO> salvarConfiguracoesNotificacao(
            @RequestBody ConfiguracaoNotificacaoRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.salvarConfiguracoesNotificacao(usuario.getId(), request));
    }

    private Usuario usuarioLogado(UserDetails userDetails) {
        return usuarioService.buscarPorEmail(userDetails.getUsername());
    }

    @GetMapping("/integracao-uft")
    public ResponseEntity<List<ConfiguracaoUftDTO>> listarConfiguracoesUft(
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.listarConfiguracoesUft(usuario.getId()));
    }

    @PutMapping("/integracao-uft/{tipoApi}")
    public ResponseEntity<Void> salvarConfiguracaoUft(
            @PathVariable String tipoApi,
            @RequestBody ConfiguracaoUftRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        adminService.salvarConfiguracaoUft(usuario.getId(), tipoApi, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/integracao-uft/{tipoApi}/testar")
    public ResponseEntity<ResultadoTesteUftDTO> testarConexaoUft(
            @PathVariable String tipoApi,
            @RequestBody ConfiguracaoUftRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioLogado(userDetails);
        return ResponseEntity.ok(adminService.testarConexaoUft(usuario.getId(), tipoApi, request));
    }
}
