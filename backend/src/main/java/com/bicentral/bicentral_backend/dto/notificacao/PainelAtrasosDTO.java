package com.bicentral.bicentral_backend.dto.notificacao;

import java.util.List;

import com.bicentral.bicentral_backend.dto.painel.GraficoSpec;

public record PainelAtrasosDTO(String departamento, GraficoSpec grafico, List<TarefaCriticaDTO> tarefas) {
}
