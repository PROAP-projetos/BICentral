package com.bicentral.bicentral_backend.dto.admin;

import java.time.OffsetDateTime;

public record UsuarioResponsavelDTO(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String usuarioEmail,
        String nomeResponsavel,
        OffsetDateTime createdAt
) {}
