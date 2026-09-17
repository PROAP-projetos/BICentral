package com.bicentral.bicentral_backend.dto.relatorio;

import dev.langchain4j.model.output.structured.Description;

public record JustificativaAcaoDTO(
        @Description("O mesmo texto da ação recebida, exatamente como fornecido")
        String acao,

        @Description("Um 'tema' curto pra essa ação, 2-5 palavras, que resuma o assunto sem o código nem o texto burocrático completo (ex: o título 'Ampliar o atendimento psicológico/pedagógico e de saúde mental, com equipes especializadas em todos os câmpus' vira 'Atendimento psicológico e de saúde mental') — usado como rótulo de tabela, precisa preservar o sentido sem distorcer")
        String tema,

        @Description("Avaliação de se o percentual baixo (0%) dessa ação reflete atraso real ou é esperado pela natureza/momento da ação — considere o título e o que ele sugere sobre dependências, ciclo do ano ou fase do projeto")
        String justificativa) {
}
