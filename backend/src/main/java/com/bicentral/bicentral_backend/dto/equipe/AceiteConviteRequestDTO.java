package com.bicentral.bicentral_backend.dto.equipe;

import jakarta.validation.constraints.NotBlank;

public record AceiteConviteRequestDTO(
        @NotBlank(message = "Token do convite é obrigatório.")
        String token
) {
}
