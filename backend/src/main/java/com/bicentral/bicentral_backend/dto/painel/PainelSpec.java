package com.bicentral.bicentral_backend.dto.painel;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record PainelSpec(
    @Description("Deve ser SEMPRE a palavra exata: 'painel'")
    String skill,
    
    @Description("O título geral do painel")
    String titulo,
    
    @Description("Lista de especificações de gráficos que compõem este painel")
    List<GraficoSpec> graficos
) {}
