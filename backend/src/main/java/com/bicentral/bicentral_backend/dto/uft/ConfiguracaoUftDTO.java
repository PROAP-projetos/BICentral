package com.bicentral.bicentral_backend.dto.uft;

import java.time.LocalDateTime;


public record ConfiguracaoUftDTO (
    Integer id,
    String tipoApi,
    String url,
    Boolean tokenConfigurado,
    Boolean ativo,
    LocalDateTime ultimaExecucao,
    String ultimoStatus,
    String ultimaMensagem
){}
