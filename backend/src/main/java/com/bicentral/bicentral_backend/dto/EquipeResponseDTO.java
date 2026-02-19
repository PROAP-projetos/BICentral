package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.Equipe;

public record EquipeResponseDTO(Long id, String nome, String descricao) {
    //construtor
    public EquipeResponseDTO(Equipe equipe){
        this(equipe.getId(), equipe.getNome(), equipe.getDescricao());
    }
}
