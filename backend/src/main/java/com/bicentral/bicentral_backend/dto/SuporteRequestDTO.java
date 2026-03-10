package com.bicentral.bicentral_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuporteRequestDTO(
        @NotBlank(message = "Nome é obrigatório.")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres.")
        String nome,

        @NotBlank(message = "E-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres.")
        String email,

        @NotBlank(message = "Assunto é obrigatório.")
        @Size(max = 160, message = "Assunto deve ter no máximo 160 caracteres.")
        String assunto,

        @NotBlank(message = "Mensagem é obrigatória.")
        @Size(min = 10, max = 4000, message = "Mensagem deve ter entre 10 e 4000 caracteres.")
        String mensagem
) {}

