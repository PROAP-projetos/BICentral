package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConviteEquipeRequestDTO(
        @NotBlank(message = "E-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres.")
        String email,

        @NotNull(message = "Selecione um papel para o convite.")
        Role role
) {
}
