package com.bicentral.bicentral_backend.dto.ia;

import java.time.OffsetDateTime;

public record TesterProiapDTO(
        Long usuarioId,
        String nome,
        String email,
        double gastoIndividual,
        double limite,
        OffsetDateTime criadoEm,
        boolean pendente) {}
