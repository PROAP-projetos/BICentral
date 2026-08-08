package com.bicentral.bicentral_backend.dto.equipe;

import jakarta.validation.constraints.NotBlank;

public record EquipeRequestDTO (
        @NotBlank String nome,
        String descricao
){}

