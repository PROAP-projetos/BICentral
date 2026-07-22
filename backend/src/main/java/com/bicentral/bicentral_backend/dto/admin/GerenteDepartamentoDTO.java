package com.bicentral.bicentral_backend.dto.admin;

import java.time.OffsetDateTime;
public record GerenteDepartamentoDTO(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String usuarioEmail,
        String departamento,
        String tipoUnidade,
        OffsetDateTime createdAt
) {}
