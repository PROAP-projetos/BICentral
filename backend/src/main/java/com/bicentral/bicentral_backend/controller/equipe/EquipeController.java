package com.bicentral.bicentral_backend.controller.equipe;


import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeResponseDTO;
import com.bicentral.bicentral_backend.dto.equipe.EquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.EquipeResponseDTO;
import com.bicentral.bicentral_backend.dto.equipe.MembroEquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.MembroEquipeResponseDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.equipe.ConviteEquipeService;
import com.bicentral.bicentral_backend.service.equipe.EquipeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipes")
public class EquipeController {
    private final EquipeService equipeService;
    private final UsuarioService usuarioService;
    private final ConviteEquipeService conviteEquipeService;

    public EquipeController(EquipeService equipeService, UsuarioService usuarioService, ConviteEquipeService conviteEquipeService){
        this.equipeService = equipeService;
        this.usuarioService = usuarioService;
        this.conviteEquipeService = conviteEquipeService;
    }
    @PostMapping
    public ResponseEntity<EquipeResponseDTO> criarEquipe(
            @RequestBody EquipeRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ){
        Usuario criador = usuarioService.buscarPorEmail(userDetails.getUsername());

        Equipe novaEquipe = new Equipe();
        novaEquipe.setNome(dto.nome());
        novaEquipe.setDescricao((dto.descricao()));

        MembroEquipe membroSalvo = equipeService.criarEquipe(novaEquipe, criador);
        return ResponseEntity.ok(new EquipeResponseDTO(membroSalvo));
    }
    @GetMapping
    public ResponseEntity<List<EquipeResponseDTO>>listarMinhasEquipes(
            @AuthenticationPrincipal UserDetails userDetails
    ){
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        List<MembroEquipe> membros = equipeService.listarEquipesUsuario(usuario.getId());

        List<EquipeResponseDTO> response = membros.stream().map(EquipeResponseDTO::new).toList();
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerEquipe(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ){
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        equipeService.deletarEquipe(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipeResponseDTO> updateEquipe(
            @PathVariable Long id,
            @RequestBody EquipeRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ){
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        MembroEquipe membroAtualizado = equipeService.updateEquipe(id, dto, usuario);
        return ResponseEntity.ok(new EquipeResponseDTO(membroAtualizado));
    }

    // --- ENDPOINTS DE MEMBROS ---

    @GetMapping("/{equipeId}/membros")
    public ResponseEntity<List<MembroEquipeResponseDTO>> listarMembros(
            @PathVariable Long equipeId
    ) {
        List<MembroEquipe> membros = equipeService.listarMembros(equipeId);
        List<MembroEquipeResponseDTO> response = membros.stream()
                .map(MembroEquipeResponseDTO::new)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{equipeId}/membros")
    public ResponseEntity<MembroEquipeResponseDTO> adicionarMembro(
            @PathVariable Long equipeId,
            @RequestBody MembroEquipeRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        MembroEquipe novoMembro = equipeService.adicionarMembro(equipeId, dto, usuarioLogado);
        return ResponseEntity.ok(new MembroEquipeResponseDTO(novoMembro));
    }

    @PostMapping("/{equipeId}/convites")
    public ResponseEntity<ConviteEquipeResponseDTO> enviarConvite(
            @PathVariable Long equipeId,
            @Valid @RequestBody ConviteEquipeRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request
    ) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        ConviteEquipeResponseDTO convite = conviteEquipeService.enviarConvite(
                equipeId,
                dto,
                usuarioLogado,
                getSiteURL(request)
        );
        return ResponseEntity.ok(convite);
    }

    @DeleteMapping("/{equipeId}/membros/{usuarioId}")
    public ResponseEntity<Void> removerMembro(
            @PathVariable Long equipeId,
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        equipeService.removerMembro(equipeId, usuarioId, usuarioLogado);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{equipeId}/membros/{usuarioId}")
    public ResponseEntity<MembroEquipeResponseDTO> alterarPapelMembro(
            @PathVariable Long equipeId,
            @PathVariable Long usuarioId,
            @RequestBody MembroEquipeRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        MembroEquipe membroAtualizado = equipeService.alterarPapelMembro(equipeId, usuarioId, dto.role(), usuarioLogado);
        return ResponseEntity.ok(new MembroEquipeResponseDTO(membroAtualizado));
    }

    private String getSiteURL(HttpServletRequest request) {
        String siteURL = request.getRequestURL().toString();
        return siteURL.replace(request.getServletPath(), "");
    }
}

