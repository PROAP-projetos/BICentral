package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record RespostaTextualDTO(
    String texto,
    List<String> fontes,
    boolean relatorioGerado,
    List<String> sugestoes
) {}