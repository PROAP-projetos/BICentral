package com.bicentral.bicentral_backend.controller.ia;

import com.bicentral.bicentral_backend.dto.ia.TesterProiapDTO;
import com.bicentral.bicentral_backend.dto.ia.UsoIaResponseDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.UsoIaService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uso-ia")
@CrossOrigin(origins = "*")
public class UsoIaController {

    private final UsoIaService usoIaService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;

    public UsoIaController(UsoIaService usoIaService, UsuarioService usuarioService, AdminService adminService) {
        this.usoIaService = usoIaService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
    }

    @GetMapping
    public UsoIaResponseDTO consultarUso(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        boolean souTester = usoIaService.ehTester(usuario.getId());
        return new UsoIaResponseDTO(usoIaService.custoDoUsuario(usuario.getId()), UsoIaService.LIMITE_DOLARES, souTester);
    }

    @GetMapping("/interacoes")
    public List<Map<String, Object>> listarInteracoes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "50") int limite) {
        exigirAdmin(userDetails);
        return usoIaService.listarInteracoes(limite);
    }

    public record FeedbackRequestDTO(String comentario) {}

    @PostMapping("/interacoes/{id}/feedback")
    public void enviarFeedback(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @RequestBody FeedbackRequestDTO requisicao) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        usoIaService.salvarFeedback(id, requisicao.comentario(), usuario.getId());
    }

    // --- Gestão de testers do proIAp (só admin) ---

    @GetMapping("/testers")
    public List<TesterProiapDTO> listarTesters(@AuthenticationPrincipal UserDetails userDetails) {
        exigirAdmin(userDetails);
        return usoIaService.listarTesters();
    }

    public record EmailRequestDTO(String email) {}

    @PostMapping("/testers")
    public Map<String, Object> adicionarTester(@AuthenticationPrincipal UserDetails userDetails, @RequestBody EmailRequestDTO requisicao) {
        exigirAdmin(userDetails);
        boolean confirmado = usoIaService.adicionarTester(requisicao.email());
        String mensagem = confirmado
                ? "Tester adicionado."
                : "E-mail adicionado. Assim que essa pessoa se cadastrar no BICentral, ela vira tester e cai direto no agente.";
        return Map.of("pendente", !confirmado, "mensagem", mensagem);
    }

    @DeleteMapping("/testers/{usuarioId}")
    public void removerTester(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long usuarioId) {
        exigirAdmin(userDetails);
        usoIaService.removerTester(usuarioId);
    }

    @DeleteMapping("/testers/pendentes")
    public void removerTesterPendente(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String email) {
        exigirAdmin(userDetails);
        usoIaService.removerTesterPendente(email);
    }

    private void exigirAdmin(UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuario.getId());
    }
}
