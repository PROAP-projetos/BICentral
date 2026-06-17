package com.bicentral.bicentral_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsultaService {

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

    public ConsultaService(
            EmbeddingModel embeddingModel, 
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        this.embeddingModel = embeddingModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    public List<String> buscar(String pergunta, Long equipeId) {
        try {
            List<Float> vetor = embeddingModel
                    .embed(TextSegment.from(pergunta))
                    .content()
                    .vectorAsList();

            return chamarFuncaoSupabase("buscar_por_local", vetor, equipeId);

        } catch (Exception e) {
            System.err.println("Modelo de embedding principal indisponível, usando Ollama: " + e.getMessage());

            try {
                List<Float> vetor = ollamaEmbeddingModel
                        .embed(TextSegment.from(pergunta))
                        .content()
                        .vectorAsList();

                return chamarFuncaoSupabase("buscar_por_ollama", vetor, equipeId);

            } catch (Exception ex) {
                throw new RuntimeException("Ambos os provedores de embedding falharam: " + ex.getMessage());
            }
        }
    }

    private List<String> chamarFuncaoSupabase(String funcao, List<Float> vetor, Long equipeId) throws Exception {
        Map<String, Object> body = Map.of(
                "query_embedding", vetor,
                "equipe_id_usuario", equipeId
        );

        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/rpc/" + funcao))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Falha na busca: " + response.body());
        }

        JsonNode resultados = MAPPER.readTree(response.body());
        return MAPPER.convertValue(
                resultados.findValuesAsText("content"),
                List.class
        );
    }

    public List<Map<String, String>> listarFontes(Long equipeId) {
        try {
            String query = "/rest/v1/embeddings?select=source,visibilidade&equipe_id=eq."
                    + URLEncoder.encode(String.valueOf(equipeId), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + query))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Falha ao listar fontes: " + response.body());
            }

            JsonNode registros = MAPPER.readTree(response.body());
            Map<String, Map<String, String>> fontes = new LinkedHashMap<>();

            for (JsonNode registro : registros) {
                String source = registro.path("source").asText("");
                if (source.isBlank()) {
                    continue;
                }

                String visibilidade = registro.path("visibilidade").asText("PUBLICO");
                fontes.putIfAbsent(source, Map.of(
                        "nome", source,
                        "acesso", visibilidade.equalsIgnoreCase("PRIVADO") ? "privado" : "publico"
                ));
            }

            return new ArrayList<>(fontes.values());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar fontes disponíveis: " + e.getMessage(), e);
        }
    }
}