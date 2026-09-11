package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record MensagemHistoricoDTO(
    String remetente,
    String texto,
    Object spec, // carrega painéis e gráficos
    List<String> fontes,
    List<String> sugestoes,
    Long interacaoId,
    boolean feedbackEnviado
) {}
