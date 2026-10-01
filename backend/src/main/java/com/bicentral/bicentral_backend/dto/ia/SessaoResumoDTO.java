package com.bicentral.bicentral_backend.dto.ia;

import java.time.OffsetDateTime;

public record SessaoResumoDTO(String id, String titulo,
OffsetDateTime criadoEm, boolean fixado){}
