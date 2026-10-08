package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

// Relatório sobre uma pessoa específica (tarefas sob sua responsabilidade), não sobre um
// departamento — forma diferente de RelatorioEstruturadoDTO de propósito: aqui a unidade de
// análise é a TAREFA individual, sem o conceito de "outros departamentos na mesma ação" nem
// análise qualitativa por item (é dado puro, igual a Lista Completa do relatório de departamento).
public record RelatorioPessoaDTO(
        String nomePessoa,
        String geradoEm,
        List<IndicadorRelatorioDTO> indicadores,
        List<TarefaPessoaDTO> tarefas) {

    /** Achatado em texto corrido, usado como contexto pro chat quando o usuário pergunta sobre um relatório de pessoa já gerado. */
    public String paraTextoLegivel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Relatório de Desempenho - ").append(nomePessoa)
          .append(" (gerado em ").append(geradoEm).append(")\n\n");

        sb.append("Indicadores:\n");
        for (IndicadorRelatorioDTO i : indicadores) {
            sb.append("- ").append(i.rotulo()).append(": ").append(i.valor()).append("\n");
        }

        sb.append("\nTarefas:\n");
        for (TarefaPessoaDTO t : tarefas) {
            sb.append("- ").append(t.acao()).append(" (").append(t.departamento()).append("): ")
              .append(t.percentualTarefa()).append("%")
              .append(t.atrasada() ? " — ATRASADA " + t.diasAtraso() + "d" : "")
              .append(" — prazo ").append(t.prazo()).append("\n");
        }

        return sb.toString();
    }
}
