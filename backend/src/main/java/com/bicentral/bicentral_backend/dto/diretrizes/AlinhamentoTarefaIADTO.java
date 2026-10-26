package com.bicentral.bicentral_backend.dto.diretrizes;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record AlinhamentoTarefaIADTO(
        @Description("O número que veio entre colchetes antes da tarefa (ex: para '[id 4821]', 4821)")
        Long id,

        @Description("ALINHADA: a tarefa contribui de forma clara para um critério. PARCIAL: toca no assunto sem cobrir o critério. SEM_EVIDENCIA: descrição vaga, ausente ou sem relação com as diretrizes da ação.")
        SituacaoAlinhamento situacao,

        @Description("Códigos das diretrizes da ação que a tarefa cobre, no formato 'INEP 1.2' ou 'IESGo 2132'. Só códigos listados na ação da tarefa. Lista vazia quando SEM_EVIDENCIA.")
        List<String> diretrizes,

        @Description("Uma frase curta: o ponto do critério que a tarefa atende (no IESGo, com a letra do item, ex: 'IESGo 2132 (A): papéis em gestão de pessoas') ou, se SEM_EVIDENCIA, por que não há relação.")
        String justificativa) {
}
