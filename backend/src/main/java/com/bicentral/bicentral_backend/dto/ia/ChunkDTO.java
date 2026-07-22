package com.bicentral.bicentral_backend.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Map;

@Data
@AllArgsConstructor
public class ChunkDTO {
    private String conteudo;   
    private String equipe;      
    private String acesso;      
    private String grupoId;     
    private String nomeArquivo;

    private Map<String, Object> metadata;
}
