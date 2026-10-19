package com.bicentral.bicentral_backend.dto.relatorio;

/**
 * {@code acao} é só o código da ação; {@code tarefa} é a descrição (ou o título, se não houver
 * descrição). Relatórios salvos antes dessa separação têm {@code acao} = "CÓDIGO - título da
 * tarefa" e {@code tituloAcao}/{@code tarefa} nulos — os geradores tratam esse formato antigo
 * na hora de baixar de novo (o JSON fica em relatorios_gerados.texto_relatorio).
 */
public record TarefaPessoaDTO(
        String acao,
        String departamento,
        Double percentualTarefa,
        boolean atrasada,
        Long diasAtraso,
        String prazo,
        String tituloAcao,
        String tarefa) {
}
