package com.bicentral.bicentral_backend.dto.diretrizes;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record AlinhamentoLoteIADTO(
        @Description("Um item para cada tarefa recebida, na mesma ordem")
        List<AlinhamentoTarefaIADTO> tarefas) {
}
