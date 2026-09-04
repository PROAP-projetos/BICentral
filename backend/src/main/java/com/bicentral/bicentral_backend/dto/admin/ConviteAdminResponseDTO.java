package com.bicentral.bicentral_backend.dto.admin;

import java.time.OffsetDateTime;

public record ConviteAdminResponseDTO(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String status,
        OffsetDateTime expiraEm
) {}