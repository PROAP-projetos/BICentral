package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

// Ação + tarefas, sem justificativa de IA nem comparação entre departamentos (isso é AcaoAnalisadaDTO).
public record AcaoComTarefasDTO(String acao, Double percentual, List<TarefaResponsavelDTO> tarefas) {
}
