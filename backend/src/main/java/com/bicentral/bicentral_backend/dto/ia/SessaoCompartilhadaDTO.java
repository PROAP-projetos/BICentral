package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record SessaoCompartilhadaDTO(String titulo, List<MensagemHistoricoDTO> mensagens) {}
