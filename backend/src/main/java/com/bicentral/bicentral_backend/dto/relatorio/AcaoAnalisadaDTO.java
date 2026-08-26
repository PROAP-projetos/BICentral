package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

public record AcaoAnalisadaDTO(
        String acao,
        Double percentual,
        List<TarefaResponsavelDTO> tarefas,
        List<DepartamentoParceiroDTO> outrosDepartamentos,
        String justificativa,
        boolean precisaAtencao) {
}
