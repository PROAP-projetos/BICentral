package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record RespostaTextual(
    String texto,
    List<String> fontes,
    boolean relatorioGerado
) {}