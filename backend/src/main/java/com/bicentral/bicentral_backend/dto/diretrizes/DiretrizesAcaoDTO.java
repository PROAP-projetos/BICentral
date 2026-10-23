package com.bicentral.bicentral_backend.dto.diretrizes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Diretrizes INEP e IESGo de uma ação do PDI, no formato de resources/diretrizes/acoes-diretrizes.json */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiretrizesAcaoDTO(
    @JsonProperty("numero_acao") String numeroAcao,
    String acao,
    String inep,
    String iesgo,
    @JsonProperty("inep_detalhes") List<IndicadorInepDTO> inepDetalhes,
    @JsonProperty("iesgo_detalhes") List<PraticaIesgoDTO> iesgoDetalhes
) {}
