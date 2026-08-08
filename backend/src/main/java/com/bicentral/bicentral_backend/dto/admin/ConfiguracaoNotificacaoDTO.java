package com.bicentral.bicentral_backend.dto.admin;

import java.math.BigDecimal;
public record ConfiguracaoNotificacaoDTO(
    Integer id, 
    BigDecimal limiteBaixoPct, 
    BigDecimal limiteBomPct
) {}
