package com.bicentral.bicentral_backend.dto.diretrizes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item (A, B, C...) de uma prática do IESGo */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemIesgoDTO(
    String item,
    String texto
) {}
