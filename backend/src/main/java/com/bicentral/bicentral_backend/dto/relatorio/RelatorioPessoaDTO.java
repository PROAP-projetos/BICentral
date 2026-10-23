package com.bicentral.bicentral_backend.dto.relatorio;

import java.util.List;

// Relatório sobre uma pessoa específica (tarefas sob sua responsabilidade), não sobre um
// departamento — forma diferente de RelatorioEstruturadoDTO de propósito: aqui a unidade de
// análise é a TAREFA individual, sem o conceito de "outros departamentos na mesma ação" nem
// análise qualitativa por item. Introdução e conclusão vêm da IA (podem ser nulas se ela falhar,
// ou em relatórios salvos antes de existirem); a referência da época fica gravada pra o gráfico
// sair igual quando o PDF é regenerado depois.
public record RelatorioPessoaDTO(
        String nomePessoa,
        String geradoEm,
        List<IndicadorRelatorioDTO> indicadores,
        List<TarefaPessoaDTO> tarefas,
        String introducao,
        String conclusao,
        Integer referenciaEpocaPct,
        String rotuloReferencia) {

    /** Achatado em texto corrido, usado como contexto pro chat quando o usuário pergunta sobre um relatório de pessoa já gerado. */
    public String paraTextoLegivel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Relatório de Desempenho - ").append(nomePessoa)
          .append(" (gerado em ").append(geradoEm).append(")\n\n");

        if (introducao != null) {
            sb.append("Introdução: ").append(introducao).append("\n\n");
        }

        sb.append("Indicadores:\n");
        for (IndicadorRelatorioDTO i : indicadores) {
            sb.append("- ").append(i.rotulo()).append(": ").append(i.valor()).append("\n");
        }

        sb.append("\nTarefas:\n");
        for (TarefaPessoaDTO t : tarefas) {
            sb.append("- ").append(t.acao());
            if (t.tarefa() != null) {
                sb.append(" - ").append(t.tarefa());
            }
            sb.append(" (").append(t.departamento()).append("): ")
              .append(t.percentualTarefa()).append("%")
              .append(t.atrasada() ? " — ATRASADA " + t.diasAtraso() + "d" : "")
              .append(" — prazo ").append(t.prazo()).append("\n");
        }

        if (conclusao != null) {
            sb.append("\nConclusão: ").append(conclusao).append("\n");
        }

        return sb.toString();
    }
}
