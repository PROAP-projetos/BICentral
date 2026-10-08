package com.bicentral.bicentral_backend.dto.painel;

public record CompartilhamentoDepartamentosDTO(
    String origem,
    String destino,
    int qtdAcoes,
    double diferencaMediaPct
) {}
