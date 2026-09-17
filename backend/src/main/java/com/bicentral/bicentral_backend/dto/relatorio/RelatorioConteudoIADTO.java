package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record RelatorioConteudoIADTO(
        @Description("Resumo executivo do relatório: 2-3 frases com o panorama geral do desempenho da unidade")
        String resumoExecutivo,

        @Description("NO MÁXIMO 3 leituras curtas e objetivas do cenário (execução geral, concentração temática dos pontos de atenção, ressalva de que 0% não significa atraso quando aplicável) — cada item é 1 frase direta, sem linguagem genérica ou redundante com o resumo executivo; pode ser menos de 3 se não houver insight genuíno pra completar")
        List<String> leituraCenario,

        @Description("Uma justificativa para CADA ação de menor execução recebida, na MESMA ORDEM e MESMA QUANTIDADE em que foram fornecidas — não pule nenhuma, não reordene")
        List<JustificativaAcaoDTO> analiseMenorExecucao,

        @Description("2 a 6 pontos de acompanhamento sugeridos a partir dos dados: cada um com um tema curto (2-4 palavras) e uma pergunta objetiva de verificação — são sugestões do sistema, não determinações oficiais, então a pergunta deve soar como algo a checar, não uma ordem")
        List<PontoAcompanhamentoDTO> pontosDeAcompanhamento) {
}
