package com.bicentral.bicentral_backend.dto.relatorio;

import dev.langchain4j.model.output.structured.Description;

public record RelatorioPessoaConteudoIADTO(
        @Description("Introdução do relatório: 2-3 frases que apresentam a pessoa no contexto do PAT — quantas tarefas tem, em quais ações/frentes elas se concentram e em que fase do ano estamos. Sem julgar desempenho ainda.")
        String introducao,

        @Description("Conclusão do relatório: 3-4 frases — o que está bem encaminhado, o que preocupa (atrasos, tarefas em 0% perto do prazo) considerando a época do ano, e 1 ou 2 prioridades objetivas para as próximas semanas. Tom profissional e respeitoso, nunca acusatório.")
        String conclusao) {
}
