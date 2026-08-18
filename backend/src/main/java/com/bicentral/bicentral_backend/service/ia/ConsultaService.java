package com.bicentral.bicentral_backend.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
