package com.bicentral.bicentral_backend.dto.diretrizes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Indicador do INEP ligado a uma ação */  
@JsonIgnoreProperties(ignoreUnknown=true)
public record IndicadorInepDTO(
    String codigo,
    String indicador,
    String criterio    
){}