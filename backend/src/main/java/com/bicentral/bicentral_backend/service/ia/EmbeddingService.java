package com.bicentral.bicentral_backend.service.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.ChunkDTO;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAG;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EmbeddingService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingModel ollamaEmbeddingModel;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public EmbeddingService(
            EmbeddingModel embeddingModel, 
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        this.embeddingModel = embeddingModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    public void salvarChunks(List<ChunkDTO> chunks, Long equipeId) {
        for (ChunkDTO chunk : chunks) {
            try {
                TextSegment segmento = TextSegment.from(chunk.getConteudo());
                
                Embedding vetorLocal = embeddingModel.embed(segmento).content();
                Embedding vetorOllama = ollamaEmbeddingModel.embed(segmento).content();
                
                salvarNoSupabase(chunk, vetorLocal.vectorAsList(), vetorOllama.vectorAsList(), equipeId);
            } catch (Exception e) {
                System.err.println("Erro ao processar chunk [" + chunk.getNomeArquivo() + "]: " + e.getMessage());
            }
        }
    }

    private void salvarNoSupabase(ChunkDTO chunk, List<Float> vetorLocal, List<Float> vetorOllama, Long equipeId) throws Exception {
        Map<String, Object> metadata = chunk.getMetadata() != null
                ? chunk.getMetadata()
                : Map.of(
                        "grupo_id", chunk.getGrupoId(),
                        "equipe", chunk.getEquipe()
                );

        Map<String, Object> body = Map.of(
                "id", UUID.randomUUID().toString(),
                "content", chunk.getConteudo(),
                "source", chunk.getNomeArquivo(),
                "equipe_id", equipeId,
                "visibilidade", chunk.getAcesso().equalsIgnoreCase("Privado") ? "PRIVADO" : "PUBLICO",
                "metadata", metadata,
                "embedding_local", vetorLocal, 
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

    // ==========================================
    // BUSCA VETORIAL DINÂMICA (RAG MULTIMODELO COM FONTES)
    // ==========================================
    public ContextoRAG buscarContextoSemelhante(String pergunta, Long equipeId, String modeloSelecionado) {
        try {
            boolean usarOllama = modeloSelecionado != null && modeloSelecionado.toLowerCase().contains("ollama");
            
            // 1. Escolhe qual modelo vai gerar o vetor da pergunta
            TextSegment segmento = TextSegment.from(pergunta);
            List<Float> vetorPergunta;
            String rpcEndpoint;

            if (usarOllama) {
                vetorPergunta = ollamaEmbeddingModel.embed(segmento).content().vectorAsList();
                rpcEndpoint = "/rest/v1/rpc/buscar_documentos_ollama"; 
            } else {
                vetorPergunta = embeddingModel.embed(segmento).content().vectorAsList();
                rpcEndpoint = "/rest/v1/rpc/buscar_documentos_local"; 
            }

            // 2. Monta o Payload para o Supabase
            Map<String, Object> body = Map.of(
                    "query_embedding", vetorPergunta,
                    "match_threshold", 0.2, 
                    "match_count", 10,       
                    "p_equipe_id", equipeId
            );

            String json = MAPPER.writeValueAsString(body);

            // 3. Dispara a requisição HTTP para a RPC correta
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + rpcEndpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // 4. Concatena os resultados e extrai as fontes
                JsonNode rootNode = MAPPER.readTree(response.body());
                StringBuilder contextoFinal = new StringBuilder();
                Set<String> fontesUnicas = new HashSet<>(); // Evita fontes duplicadas

                for (JsonNode node : rootNode) {
                    if (node.has("content") && !node.get("content").isNull()) {
                        contextoFinal.append(node.get("content").asText()).append("\n\n");
                    }
                    if (node.has("source") && !node.get("source").isNull()) {
                        fontesUnicas.add(node.get("source").asText());
                    }
                }
                
                return new ContextoRAG(contextoFinal.toString().trim(), new ArrayList<>(fontesUnicas));
            } else {
                System.err.println("Erro na busca vetorial: " + response.body());
                return new ContextoRAG("", new ArrayList<>());
            }

        } catch (Exception e) {
            System.err.println("Erro de conexão ao buscar contexto: " + e.getMessage());
            return new ContextoRAG("", new ArrayList<>());
        }
    }
}