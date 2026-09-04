package com.bicentral.bicentral_backend.dto.painel;

import java.util.List;

public record PainelRespostaDTO(
    String skill,
    String mensagemContexto,
    String titulo,
    List<GraficoSpecDTO> graficos,
    Boolean aguardandoConfirmacao,
    Long interacaoId
) {}
