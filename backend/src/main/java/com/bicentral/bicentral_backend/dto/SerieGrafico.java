package com.bicentral.bicentral_backend.dto;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record SerieGrafico(
    @Description("O nome da métrica sendo exibida, ex: 'Matrículas' ou 'Orçamento'")
    String nome,
    
    @Description("Lista de valores numéricos correspondentes às categorias do eixo X")
    List<Number> valores
) {}