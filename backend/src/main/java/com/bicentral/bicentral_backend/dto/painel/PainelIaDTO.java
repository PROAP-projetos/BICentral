package com.bicentral.bicentral_backend.dto.painel;

import java.time.OffsetDateTime;

// Um painel gerado pela IA e salvo pelo usuário — aparece na categoria "Painéis de IA",
// ao lado dos painéis do Power BI (que são outra coisa: um link de embed, não gráfico de verdade).
public record PainelIaDTO(
    Long id,
    String titulo,
    PainelSpecDTO especificacao,
    OffsetDateTime criadoEm
) {}
