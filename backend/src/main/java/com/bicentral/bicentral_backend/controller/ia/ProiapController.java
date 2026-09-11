package com.bicentral.bicentral_backend.controller.ia;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import com.bicentral.bicentral_backend.dto.ia.MensagemHistoricoDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoCompartilhadaDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoResumoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.PainelAtrasosDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.state.EstadoSessao;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;

import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.ChatHistoricoService;
import com.bicentral.bicentral_backend.service.ia.ProiapService;
import com.bicentral.bicentral_backend.service.notificacao.NotificacaoService;


import java.util.List;

@RestController
@RequestMapping("/api/proiap")
@CrossOrigin(origins = "*")
public class ProiapController {

    private final ProiapService proiapService;
    private final EstadoSessao estadoSessao;
    private final NotificacaoService notificacaoService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;
    private final ChatHistoricoService chatHistoricoService;

    public ProiapController(ProiapService proiapService, EstadoSessao estadoSessao, NotificacaoService notificacaoService, 
        UsuarioService usuarioService, AdminService adminService, ChatHistoricoService chatHistoricoService) {
        this.proiapService = proiapService;
        this.estadoSessao = estadoSessao;
        this.notificacaoService = notificacaoService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
        this.chatHistoricoService = chatHistoricoService;
    }

    public record RequisicaoProiap(String texto, Long equipeId, String modelo, String sessaoId) {
    }

    @PostMapping("/perguntar")
    public Object fazerPergunta(@RequestBody RequisicaoProiap requisicao,
    @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada, faça login novamente");
        }

        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        boolean usuarioEhAdmin = adminService.isAdmin(usuario.getId());

        if (requisicao.modelo() != null) {
            estadoSessao.setModelo(requisicao.modelo());
        }

        if (requisicao.equipeId() != null) {
            estadoSessao.setEquipeId(requisicao.equipeId());
        }

        return proiapService.processarPergunta(requisicao.texto(), requisicao.sessaoId(), usuarioEhAdmin, usuario.getId());
    }

    // Endpoint de debug (deixa ver a notificação de qualquer usuário, não só a sua) —
    // por isso exige admin, mesmo já exigindo login por padrão em todo /api/proiap/**.
    @GetMapping("/testar-notificacoes/{usuarioId}")
    public List<NotificacaoDTO> testarNotificacoes(@PathVariable Long usuarioId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuarioLogado.getId());
        return notificacaoService.gerarNotificacoes(usuarioId);
    }

    @GetMapping("/sessoes")
    public List<SessaoResumoDTO> listarSessoes(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return chatHistoricoService.listarSessoes(usuario.getId());
    }

    @GetMapping("/sessoes/{sessaoId}/mensagens")
    public List<MensagemHistoricoDTO> listarMensagens(@PathVariable String sessaoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return chatHistoricoService.listarMensagens(sessaoId, usuario.getId());
    }

    @DeleteMapping("/sessoes/{sessaoId}")
    public void excluirSessao(@PathVariable String sessaoId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.excluirSessao(sessaoId, usuario.getId());
    }

    public record TituloRequestDTO(String titulo) {}

    @PatchMapping("/sessoes/{sessaoId}/titulo")
    public void renomearSessao(@PathVariable String sessaoId, @RequestBody TituloRequestDTO requisicao,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.renomear(sessaoId, usuario.getId(), requisicao.titulo());
    }

    public record FlagRequestDTO(boolean valor) {}

    @PatchMapping("/sessoes/{sessaoId}/fixar")
    public void fixarSessao(@PathVariable String sessaoId, @RequestBody FlagRequestDTO requisicao,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.fixar(sessaoId, usuario.getId(), requisicao.valor());
    }

    public record TokenResponseDTO(String token) {}

    @PostMapping("/sessoes/{sessaoId}/compartilhar")
    public TokenResponseDTO compartilharSessao(@PathVariable String sessaoId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return new TokenResponseDTO(chatHistoricoService.compartilhar(sessaoId, usuario.getId()));
    }

    // Rota pública (ver "/api/proiap/compartilhado/**" no SecurityConfig) — quem abre o link
    // não precisa ter conta nem estar logado no BICentral.
    @GetMapping("/compartilhado/{token}")
    public SessaoCompartilhadaDTO buscarCompartilhada(@PathVariable String token) {
        return chatHistoricoService.buscarCompartilhada(token);
    }
}