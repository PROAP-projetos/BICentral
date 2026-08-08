package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record ContextoRAG (
    String textoContexto,
    List<String> fontes    
) {}
