package com.bicentral.bicentral_backend.dto.painel;

public record TarefaGrafoDTO(
    String titulo,
    String responsavel,
    Double percentual,
    String status // "concluida" | "em_andamento" | "atrasada"
) {}
