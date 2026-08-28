package com.bicentral.bicentral_backend.dto.tarefas;

public record TarefasAtrasadasResumoDTO (
    int quantidade,
    String tituloMaisUrgente,
    Integer diasAtraso
){}
