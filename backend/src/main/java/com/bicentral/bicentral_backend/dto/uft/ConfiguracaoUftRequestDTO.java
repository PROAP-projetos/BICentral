package com.bicentral.bicentral_backend.dto.uft;

public record ConfiguracaoUftRequestDTO(
    String url,
    String token,
    Boolean ativo
) {}
