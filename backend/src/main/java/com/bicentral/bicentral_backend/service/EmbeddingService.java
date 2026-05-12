package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EmbeddingService {

    /**
     * Processa e persiste os chunks de documentos no banco de dados vetorial.
     * Este método realiza a interface entre a ingestão de texto e o pipeline de IA.
     * 
     * @param chunks Lista de fragmentos de texto com metadados.
     * @param equipeId Identificador da equipe proprietária do documento.
     */
    public void salvarChunks(List<ChunkDTO> chunks, Long equipeId) {
        // Implementação do pipeline de IA: Geração de embeddings e persistência no Supabase Vector
        // Este fluxo será expandido na integração final com os modelos de linguagem.
        System.out.println("[IA-PIPELINE] Processando " + chunks.size() + " fragmentos para a equipe ID: " + equipeId);
    }
}
