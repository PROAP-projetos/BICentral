package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

public record RelatorioEstruturadoDTO(
        String departamento,
        String tipo,
        String geradoEm,
        String resumoExecutivo,
        List<String> leituraCenario,
        List<IndicadorRelatorioDTO> indicadores,
        DistribuicaoExecucaoDTO distribuicao,
        List<AcaoAnalisadaDTO> analiseMenorExecucao,
        List<AcaoRelatorioDTO> destaquesPositivos,
        List<PontoAcompanhamentoDTO> pontosDeAcompanhamento) {

    /**
     * Achata o relatório estruturado em texto corrido, usado só como contexto
     * pro chat quando o usuário pergunta sobre um relatório que já gerou.
     */
    public String paraTextoLegivel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Relatório de Desempenho - ").append(departamento)
          .append(" (").append(tipo).append(", gerado em ").append(geradoEm).append(")\n\n");

        sb.append("Resumo executivo: ").append(resumoExecutivo).append("\n\n");

        if (leituraCenario != null && !leituraCenario.isEmpty()) {
            sb.append("Leitura do cenário:\n");
            for (String l : leituraCenario) {
                sb.append("- ").append(l).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Indicadores gerais:\n");
        for (IndicadorRelatorioDTO i : indicadores) {
            sb.append("- ").append(i.rotulo()).append(": ").append(i.valor()).append("\n");
        }
        if (distribuicao != null) {
            sb.append("- Distribuição: ").append(distribuicao.semExecucao()).append(" sem execução, ")
              .append(distribuicao.emExecucao()).append(" em execução, ")
              .append(distribuicao.concluidas()).append(" concluídas (").append(distribuicao.total()).append(" no total)\n");
        }

        sb.append("\nAnálise das ações com menor execução:\n");
        for (AcaoAnalisadaDTO a : analiseMenorExecucao) {
            sb.append("- ").append(a.precisaAtencao() ? "[PRECISA ATENÇÃO] " : "").append(a.acao())
              .append(" (").append(a.percentual()).append("%): ").append(a.justificativa()).append("\n");
            for (TarefaResponsavelDTO t : a.tarefas()) {
                // "título" é um rótulo genérico repetido por tarefas reais e distintas da mesma
                // ação — "descrição" é o que diferencia de verdade uma da outra.
                String texto = t.descricao() != null && !t.descricao().isBlank() ? t.descricao() : t.titulo();
                sb.append("    tarefa: ").append(texto).append(" — ").append(t.responsavel())
                  .append(" — prazo ").append(t.prazo()).append("\n");
            }
            for (DepartamentoParceiroDTO d : a.outrosDepartamentos()) {
                sb.append("    também responsável: ").append(d.departamento()).append(" (").append(d.percentual()).append("%)\n");
            }
        }

        sb.append("\nDestaques positivos:\n");
        for (AcaoRelatorioDTO a : destaquesPositivos) {
            sb.append("- ").append(a.acao()).append(": ").append(a.percentual()).append("%\n");
        }

        sb.append("\nPontos de acompanhamento:\n");
        for (PontoAcompanhamentoDTO p : pontosDeAcompanhamento) {
            sb.append("- ").append(p.tema()).append(": ").append(p.oQueVerificar()).append("\n");
        }

        return sb.toString();
    }
}
