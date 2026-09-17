package com.bicentral.bicentral_backend.controller.ia;

import com.bicentral.bicentral_backend.dto.ia.TesterProiapDTO;
import com.bicentral.bicentral_backend.dto.ia.UsoIaResponseDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.auth.EmailService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.UsoIaService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
public class UsoIaController {

    private final UsoIaService usoIaService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;
    private final EmailService emailService;

    public UsoIaController(UsoIaService usoIaService, UsuarioService usuarioService, AdminService adminService, EmailService emailService) {
        this.usoIaService = usoIaService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
        this.emailService = emailService;
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

    // Status do disparo por versão — o front usa isso pra saber se o botão já "morreu"
    // mesmo depois de um F5 ou login em outra aba/máquina.
    @GetMapping("/testers/notificar-versao")
    public Map<String, Object> statusNotificacaoVersao(
            @AuthenticationPrincipal UserDetails userDetails, @RequestParam String versao) {
        exigirAdmin(userDetails);
        return Map.of("enviado", usoIaService.versaoJaNotificada(versao));
    }

    // Dispara o e-mail de "nova versão" pra todos os testers confirmados (ignora pendentes,
    // que ainda não têm conta/nome pra personalizar o e-mail). Best-effort por pessoa: um
    // envio falhando não derruba os demais nem a requisição (ver sendVersaoAnuncioEmailAsync).
    // Idempotente por versão: uma segunda chamada (duplo clique, F5, outra aba) não reenvia.
    @PostMapping("/testers/notificar-versao")
    public Map<String, Object> notificarVersaoParaTesters(
            @AuthenticationPrincipal UserDetails userDetails, @RequestParam String versao) {
        exigirAdmin(userDetails);
        if (!usoIaService.marcarVersaoNotificada(versao)) {
            return Map.of("enviados", 0, "jaEnviado", true);
        }
        List<TesterProiapDTO> destinatarios = usoIaService.listarTesters().stream()
                .filter(t -> !t.pendente() && t.email() != null && !t.email().isBlank())
                .toList();
        for (TesterProiapDTO tester : destinatarios) {
            emailService.sendVersaoAnuncioEmailAsync(tester.email(), tester.nome() != null ? tester.nome() : "tester");
        }
        return Map.of("enviados", destinatarios.size(), "jaEnviado", false);
    }

    private void exigirAdmin(UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuario.getId());
    }
}
