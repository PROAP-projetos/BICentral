package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record RelatorioConteudoIADTO(
        @Description("Resumo executivo do relatório: 2-3 frases com o panorama geral do desempenho da unidade")
        String resumoExecutivo,

        @Description("Uma justificativa para CADA ação de menor execução recebida, na MESMA ORDEM e MESMA QUANTIDADE em que foram fornecidas — não pule nenhuma, não reordene")
        List<JustificativaAcaoDTO> analiseMenorExecucao,

        @Description("Recomendações objetivas e acionáveis, uma por item da lista, tom profissional para gestores públicos")
        List<String> recomendacoes) {
}
