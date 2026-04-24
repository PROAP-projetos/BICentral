package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.ConviteEquipe;
import com.bicentral.bicentral_backend.model.Role;

import java.time.LocalDateTime;

public record ConviteEquipeResponseDTO(
        Long id,
        Long equipeId,
        String equipeNome,
        String email,
        Role role,
        ConviteEquipe.Status status,
        LocalDateTime expiraEm
) {
    public ConviteEquipeResponseDTO(ConviteEquipe convite) {
        this(
                convite.getId(),
                convite.getEquipe().getId(),
                convite.getEquipe().getNome(),
                convite.getEmail(),
                convite.getRole(),
                convite.getStatus(),
                convite.getExpiraEm()
        );
    }
}
