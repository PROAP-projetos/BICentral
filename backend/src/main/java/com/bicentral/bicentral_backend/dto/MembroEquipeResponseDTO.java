package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;

public record MembroEquipeResponseDTO(Long usuarioId, String email, String nome, Role role) {
    public MembroEquipeResponseDTO(MembroEquipe membro) {
        this(membro.getUsuario().getId(), membro.getUsuario().getEmail(), membro.getUsuario().getNome(), membro.getRole());
    }
}
