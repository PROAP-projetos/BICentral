package com.bicentral.bicentral_backend.dto.relatorio;

import dev.langchain4j.model.output.structured.Description;

public record JustificativaAcaoDTO(
        @Description("O mesmo texto da ação recebida, exatamente como fornecido")
        String acao,

        @Description("Avaliação de se o percentual baixo (0%) dessa ação reflete atraso real ou é esperado pela natureza/momento da ação — considere o título e o que ele sugere sobre dependências, ciclo do ano ou fase do projeto")
        String justificativa) {
}
