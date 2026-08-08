package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

public record AcaoAnalisada(
        String acao,
        Double percentual,
        List<TarefaResponsavel> tarefas,
        List<DepartamentoParceiro> outrosDepartamentos,
        String justificativa,
        boolean precisaAtencao) {
}
