package com.bicentral.bicentral_backend.controller;


import com.bicentral.bicentral_backend.dto.EquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.EquipeResponseDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.EquipeService;
import com.bicentral.bicentral_backend.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/equipes")
public class EquipeController {
    private final EquipeService equipeService;
    private final UsuarioService usuarioService;

    public EquipeController(EquipeService equipeService, UsuarioService usuarioService){
        this.equipeService = equipeService;
        this.usuarioService = usuarioService;
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

        Equipe equipeSalva = equipeService.criarEquipe(novaEquipe, criador);
        return ResponseEntity.ok(new EquipeResponseDTO(equipeSalva));
    }
    @GetMapping
    public ResponseEntity<List<EquipeResponseDTO>>listarMinhasEquipes(
            @AuthenticationPrincipal UserDetails userDetails
    ){
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        List<Equipe> equipes = equipeService.listarEquipesUsuario(usuario.getId());

        List<EquipeResponseDTO> response = equipes.stream().map(EquipeResponseDTO::new).toList();
        return ResponseEntity.ok(response);
    }
}
