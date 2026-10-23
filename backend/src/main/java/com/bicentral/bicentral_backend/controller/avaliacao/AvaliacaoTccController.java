package com.bicentral.bicentral_backend.controller.avaliacao;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.avaliacao.AvaliacaoTccService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avaliacao-tcc")
public class AvaliacaoTccController {

    private final AvaliacaoTccService avaliacaoTccService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;

    public AvaliacaoTccController(AvaliacaoTccService avaliacaoTccService, UsuarioService usuarioService, AdminService adminService) {
        this.avaliacaoTccService = avaliacaoTccService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
    }

    @GetMapping("/itens")
    public Map<String, Object> listarItens(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return Map.of(
                "itens", avaliacaoTccService.listarItens(usuario.getId()),
                "souAdmin", adminService.isAdmin(usuario.getId()));
    }

    public record RespostaRequestDTO(Boolean correta, Boolean completa, String tempoManual, String comentario) {
    }

    @PostMapping("/itens/{itemId}/resposta")
    public void salvarResposta(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String itemId,
                               @RequestBody RespostaRequestDTO requisicao) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        avaliacaoTccService.salvarResposta(itemId, usuario.getId(), requisicao.correta(), requisicao.completa(),
                requisicao.tempoManual(), requisicao.comentario());
    }

    @PostMapping("/itens")
    public Map<String, Object> importarItens(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody List<AvaliacaoTccService.ItemDTO> itens) {
        exigirAdmin(userDetails);
        return Map.of("importados", avaliacaoTccService.importarItens(itens));
    }

    @GetMapping("/resultado")
    public List<Map<String, Object>> resultado(@AuthenticationPrincipal UserDetails userDetails) {
        exigirAdmin(userDetails);
        return avaliacaoTccService.resultado();
    }

    private void exigirAdmin(UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuario.getId());
    }
}
