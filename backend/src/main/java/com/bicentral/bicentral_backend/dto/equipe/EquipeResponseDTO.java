package com.bicentral.bicentral_backend.dto.equipe;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;

public record EquipeResponseDTO(Long id, String nome, String descricao, Role role) {
    //construtor
    public EquipeResponseDTO(Equipe equipe, Role role){
        this(equipe.getId(), equipe.getNome(), equipe.getDescricao(), role);
    }
    public EquipeResponseDTO(MembroEquipe membro){
        this(membro.getEquipe().getId(), membro.getEquipe().getNome(), membro.getEquipe().getDescricao(), membro.getRole());
    }
}
