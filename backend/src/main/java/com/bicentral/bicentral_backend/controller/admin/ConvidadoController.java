package com.bicentral.bicentral_backend.controller.admin;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.admin.ConvidadoService;
import com.bicentral.bicentral_backend.service.admin.ConvidadoService.ConvidadoDTO;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;

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
@RequestMapping("/api/admin/convidados")
public class ConvidadoController {

    public record EmailRequestDTO(String email) {}

    private final ConvidadoService convidadoService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;

    public ConvidadoController(ConvidadoService convidadoService, UsuarioService usuarioService, AdminService adminService) {
        this.convidadoService = convidadoService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
    }

    @GetMapping
    public List<ConvidadoDTO> listar(@AuthenticationPrincipal UserDetails userDetails) {
        exigirAdmin(userDetails);
        return convidadoService.listar();
    }

    @PostMapping
    public Map<String, Object> adicionar(@AuthenticationPrincipal UserDetails userDetails, @RequestBody EmailRequestDTO requisicao) {
        exigirAdmin(userDetails);
        boolean confirmado = convidadoService.adicionar(requisicao.email());
        String mensagem = confirmado
                ? "Convidado adicionado."
                : "E-mail convidado. Assim que essa pessoa se cadastrar no BICentral, ela vira convidada.";
        return Map.of("pendente", !confirmado, "mensagem", mensagem);
    }

    @DeleteMapping("/{usuarioId}")
    public void remover(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long usuarioId) {
        exigirAdmin(userDetails);
        convidadoService.remover(usuarioId);
    }

    @DeleteMapping("/pendentes")
    public void removerPendente(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String email) {
        exigirAdmin(userDetails);
        convidadoService.removerPendente(email);
    }

    private void exigirAdmin(UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuario.getId());
    }
}
