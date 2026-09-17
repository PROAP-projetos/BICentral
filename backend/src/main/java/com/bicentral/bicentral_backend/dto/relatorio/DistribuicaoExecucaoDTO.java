package com.bicentral.bicentral_backend.dto.relatorio;

/** Contagem de TODAS as ações da unidade por faixa de execução — não só as 15 piores/melhores exibidas no corpo do relatório. Usado pra visão executiva (números grandes + distribuição). */
public record DistribuicaoExecucaoDTO(int semExecucao, int emExecucao, int concluidas) {

    public int total() {
        return semExecucao + emExecucao + concluidas;
    }
}
