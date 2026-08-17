package com.bicentral.bicentral_backend.dto.notificacao;

import java.util.List;

import com.bicentral.bicentral_backend.dto.painel.GraficoSpecDTO;

public record PainelAtrasosDTO(String departamento, GraficoSpecDTO grafico, List<TarefaCriticaDTO> tarefas) {
}
