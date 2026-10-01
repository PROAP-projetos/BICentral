package com.bicentral.bicentral_backend.dto.relatorio;

public record TarefaPessoaDTO(
        String acao,
        String departamento,
        Double percentualTarefa,
        boolean atrasada,
        Long diasAtraso,
        String prazo) {
}
