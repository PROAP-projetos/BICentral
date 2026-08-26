package com.bicentral.bicentral_backend.dto.painel;

public record RankingDepartamentoDTO(
    String departamento,
    String tipoUnidade,
    double mediaExecucaoPct,
    int qtdAcoes,
    int posicaoAtual,
    Integer posicaoAnterior
) {}
