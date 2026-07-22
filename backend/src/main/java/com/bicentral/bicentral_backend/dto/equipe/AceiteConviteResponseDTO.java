package com.bicentral.bicentral_backend.dto.equipe;

import com.bicentral.bicentral_backend.model.Role;

public record AceiteConviteResponseDTO(
        String mensagem,
        Long equipeId,
        String equipeNome,
        String email,
        Role role
) {
}
