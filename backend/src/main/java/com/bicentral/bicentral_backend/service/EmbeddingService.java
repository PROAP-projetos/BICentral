package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmbeddingService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final EmbeddingModel geminiEmbeddingModel;
    private final EmbeddingModel ollamaEmbeddingModel;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public EmbeddingService(
            @Qualifier("geminiEmbeddingModel") EmbeddingModel geminiEmbeddingModel,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        this.geminiEmbeddingModel = geminiEmbeddingModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    /**
     * Processa e persiste os chunks de documentos no banco de dados vetorial (Supabase).
     * Gera embeddings utilizando tanto Gemini quanto Ollama para redundância/comparação.
     * 
     * @param chunks Lista de fragmentos de texto com metadados.
     * @param equipeId Identificador da equipe proprietária do documento.
     */
    public void salvarChunks(List<ChunkDTO> chunks, Long equipeId) {
        for (ChunkDTO chunk : chunks) {
            try {
                TextSegment segmento = TextSegment.from(chunk.getConteudo());
                
                // Geração de vetores
                Embedding vetorGemini = geminiEmbeddingModel.embed(segmento).content();
                Embedding vetorOllama = ollamaEmbeddingModel.embed(segmento).content();
                
                // Persistência no banco de dados vetorial
                salvarNoSupabase(chunk, vetorGemini.vectorAsList(), vetorOllama.vectorAsList(), equipeId);
            } catch (Exception e) {
                System.err.println("Erro ao processar chunk [" + chunk.getNomeArquivo() + "]: " + e.getMessage());
            }
        }
    }

    private void salvarNoSupabase(ChunkDTO chunk, List<Float> vetorGemini, List<Float> vetorOllama, Long equipeId) throws Exception {
        Map<String, Object> body = Map.of(
                "id", UUID.randomUUID().toString(),
                "content", chunk.getConteudo(),
                "source", chunk.getNomeArquivo(),
                "equipe_id", equipeId,
                "visibilidade", chunk.getAcesso().equalsIgnoreCase("Privado") ? "PRIVADO" : "PUBLICO",
                "metadata", Map.of(
                        "grupo_id", chunk.getGrupoId(),
                        "equipe", chunk.getEquipe()
                ),
                "embedding_gemini", vetorGemini,
                "embedding_ollama", vetorOllama
        );

        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/embeddings"))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Falha ao salvar no Supabase: " + response.body());
        }
    }
}
