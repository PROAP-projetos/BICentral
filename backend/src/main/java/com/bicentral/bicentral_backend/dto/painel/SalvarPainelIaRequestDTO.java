package com.bicentral.bicentral_backend.dto.painel;

// Corpo do POST /api/paineis-ia — o frontend manda de volta o mesmo PainelSpecDTO que
// já recebeu do chat, não pede pra IA gerar de novo.
public record SalvarPainelIaRequestDTO(
    String titulo,
    PainelSpecDTO especificacao
) {}
