package com.bicentral.bicentral_backend.dto.admin;

import java.math.BigDecimal;
public record ConfiguracaoNotificacaoRequestDTO(
    BigDecimal limiteBaixoPct, 
    BigDecimal limiteBomPct
) {}
