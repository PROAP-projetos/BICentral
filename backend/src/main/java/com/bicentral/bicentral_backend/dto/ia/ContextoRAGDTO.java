package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record ContextoRAGDTO (
    String textoContexto,
    List<String> fontes    
) {}
