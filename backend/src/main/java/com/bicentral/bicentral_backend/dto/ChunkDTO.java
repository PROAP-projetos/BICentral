package com.bicentral.bicentral_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkDTO {
    private String conteudo;    // O texto do fatiamento
    private String equipe;      // Ex: "PROAD", "SUCOM"
    private String acesso;      // Ex: "Público" ou "Privado"
    private String grupoId;     // ID para agrupar chunks do mesmo arquivo
    private String nomeArquivo; // Para rastreabilidade (LGPD)
}
