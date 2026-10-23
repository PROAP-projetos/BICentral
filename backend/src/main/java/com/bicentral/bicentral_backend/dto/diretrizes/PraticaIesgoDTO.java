package com.bicentral.bicentral_backend.dto.diretrizes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Prática do questionário de governança IESGo (ex: 1111) com seus itens */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PraticaIesgoDTO(
    String codigo,
    List<ItemIesgoDTO> itens
) {}
