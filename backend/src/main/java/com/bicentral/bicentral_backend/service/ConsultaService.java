package com.bicentral.bicentral_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class ConsultaService {

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

    public ConsultaService(
            @Qualifier("geminiEmbeddingModel") EmbeddingModel geminiEmbeddingModel,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        this.geminiEmbeddingModel = geminiEmbeddingModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    public List<String> buscar(String pergunta, Long equipeId) {
        try {
            List<Float> vetor = geminiEmbeddingModel
                    .embed(TextSegment.from(pergunta))
                    .content()
                    .vectorAsList();

            return chamarFuncaoSupabase("buscar_por_gemini", vetor, equipeId);

        } catch (Exception e) {
            System.err.println("Gemini indisponível, usando Ollama: " + e.getMessage());

            try {
                List<Float> vetor = ollamaEmbeddingModel
                        .embed(TextSegment.from(pergunta))
                        .content()
                        .vectorAsList();

                return chamarFuncaoSupabase("buscar_por_ollama", vetor, equipeId);

            } catch (Exception ex) {
                throw new RuntimeException("Ambos os provedores falharam: " + ex.getMessage());
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
}
