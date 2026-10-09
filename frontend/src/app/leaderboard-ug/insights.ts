// O roteiro vai no próprio texto da pergunta: o agente escolhe as ferramentas a partir dele, sem rota fixa no backend.
export function promptDiagnostico(nomeUnidade: string): string {
  return `Faça um diagnóstico da execução do PAT de ${nomeUnidade}. `
    + 'Consulte o percentual da unidade, o detalhamento do desempenho por ação e, se houver ação compartilhada parada, rastreie onde está o gargalo. '
    + 'Responda nesta ordem: 1) uma frase com o percentual e a posição no formato "Xª de N" informada no panorama (ranking de UGs, sem coordenações), citando empates; '
    + '2) tabela com no máximo 8 ações de menor execução, com a coluna "% na unidade" (o percentual do PAT da unidade) e a coluna "% geral da ação", '
    + 'esta preenchida só para ação compartilhada (o geral entre as unidades responsáveis); ação de um setor só não tem percentual geral, deixe "—"; '
    + '3) 2 ou 3 pontos de atenção; 4) uma recomendação objetiva, priorizando ações paradas em 0% que outras unidades também executam, antes de ações que já têm algum avanço.';
}

export function promptResumoExecutivo(): string {
  return 'Gere o resumo executivo do PAT da instituição, com os números exatamente como vierem da consulta do resumo executivo. '
    + 'Responda nesta ordem: 1) uma frase com a situação geral (total de ações únicas, quantas concluídas, em andamento e paradas), a média de execução das UGs comparada com a referência esperada para a época do ano (diga a distância entre as duas) e a data de atualização dos dados; '
    + '2) tabela pequena das melhores e das piores UGs com percentual e posição no formato "Xª de N"; UGs empatadas ficam juntas numa linha só, como vieram, sem inventar posição individual para cada uma; '
    + '3) riscos: quantas UGs estão abaixo de 60% e as ações compartilhadas paradas em 0% em todas as unidades, com o código; '
    + '4) uma recomendação objetiva com 2 ou 3 prioridades, apontando quem está em 0% e as ações compartilhadas paradas; '
    + 'evite recomendações genéricas como "acompanhar todas as UGs abaixo de 60%" e, se a maioria das UGs estiver nessa faixa, diga isso como contexto e aponte o que puxa a média para baixo.';
}

export function promptMaiorXMenor(maior: string, menor: string): string {
  return `Compare a execução do PAT entre ${maior} e ${menor}, usando os números exatamente como vierem da consulta de comparação das duas unidades. `
    + 'Responda nesta ordem: 1) uma frase com a diferença de média em pontos percentuais e a posição de cada uma no formato "Xª de N"; '
    + '2) tabela lado a lado com média, posição, total de ações, concluídas, em andamento e zeradas; '
    + '3) as ações em comum de maior diferença, numa tabela com o percentual de cada unidade, dizendo quem está à frente; se não houver ação em comum, diga isso; '
    + '4) 2 ou 3 observações sobre o que pode explicar a diferença, sempre como hipótese e considerando a época do ano; '
    + '5) uma recomendação objetiva para a unidade de menor execução.';
}

export function promptAcoesSemExecucao(): string {
  return 'Quais UGs têm mais ações sem nenhuma execução no PAT? Use os números exatamente como vierem da consulta de ações sem execução por UG. '
    + 'Responda nesta ordem: 1) uma frase com quantas UGs têm ação zerada e o total de atribuições zeradas; '
    + '2) tabela com UG, total de ações, zeradas, percentual de zeradas e zeradas compartilhadas; '
    + '3) leitura curta: quais UGs concentram o problema em quantidade e quais têm proporção alta mesmo com poucas ações, considerando a época do ano; '
    + '4) uma recomendação objetiva, começando pelas ações zeradas compartilhadas, que travam mais de uma unidade.';
}
