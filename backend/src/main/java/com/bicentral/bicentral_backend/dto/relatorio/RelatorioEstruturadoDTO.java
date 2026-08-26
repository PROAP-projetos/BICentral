package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

public record RelatorioEstruturadoDTO(
        String departamento,
        String tipo,
        String geradoEm,
        String resumoExecutivo,
        List<IndicadorRelatorioDTO> indicadores,
        List<AcaoAnalisadaDTO> analiseMenorExecucao,
        List<AcaoRelatorioDTO> destaquesPositivos,
        List<String> recomendacoes) {

    /**
     * Achata o relatório estruturado em texto corrido, usado só como contexto
     * pro chat quando o usuário pergunta sobre um relatório que já gerou.
     */
    public String paraTextoLegivel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Relatório de Desempenho - ").append(departamento)
          .append(" (").append(tipo).append(", gerado em ").append(geradoEm).append(")\n\n");

        sb.append("Resumo executivo: ").append(resumoExecutivo).append("\n\n");

        sb.append("Indicadores gerais:\n");
        for (IndicadorRelatorioDTO i : indicadores) {
            sb.append("- ").append(i.rotulo()).append(": ").append(i.valor()).append("\n");
        }

        sb.append("\nAnálise das ações com menor execução:\n");
        for (AcaoAnalisadaDTO a : analiseMenorExecucao) {
            sb.append("- ").append(a.precisaAtencao() ? "[PRECISA ATENÇÃO] " : "").append(a.acao())
              .append(" (").append(a.percentual()).append("%): ").append(a.justificativa()).append("\n");
            for (TarefaResponsavelDTO t : a.tarefas()) {
                sb.append("    tarefa: ").append(t.titulo()).append(" — ").append(t.responsavel())
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

        sb.append("\nRecomendações:\n");
        for (String r : recomendacoes) {
            sb.append("- ").append(r).append("\n");
        }

        return sb.toString();
    }
}
