package com.bicentral.bicentral_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.http.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;


@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String SUPABASE_URL;

    @Value("${supabase.key}")
    private String SUPABASE_KEY;

    @Value("${supabase.bucket}")
    private String BUCKET;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Duration SIGNED_URL_SAFETY_WINDOW = Duration.ofSeconds(30);

    private record SignedUrlCacheEntry(String signedUrl, Instant expiresAt) {}

    private final ConcurrentHashMap<String, SignedUrlCacheEntry> signedUrlCache = new ConcurrentHashMap<>();

    private String getApiEndpoint() {
        return SUPABASE_URL + "/storage/v1/object/";
    }

    public String uploadFile(String pathInBucket, Path localFilePath) throws Exception {
        byte[] fileBytes = Files.readAllBytes(localFilePath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiEndpoint() + BUCKET + "/" + pathInBucket))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "image/png")
                .header("x-upsert", "true")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        HttpResponse<String> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return pathInBucket; // agora eu aguardo só o caminho do arquivo (migração de public para private)
        } else {
            throw new RuntimeException("Falha no upload: " + response.body());
        }
    }

    public void deleteFile(String pathInBucket) throws Exception {
        if (pathInBucket == null || pathInBucket.isBlank()) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiEndpoint() + BUCKET + "/" + pathInBucket))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .DELETE()
                .build();

        HttpResponse<String> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Falha no delete: " + response.body());
        }
    }

    public String createSignedUrl(String pathInBucket, int expiresInSeconds) throws Exception {
        if (pathInBucket == null || pathInBucket.isBlank()) {
            throw new IllegalArgumentException("pathInBucket");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds");
        }

        SignedUrlCacheEntry cached = signedUrlCache.get(pathInBucket);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.signedUrl();
        }

        // Endpoint mais compatível (e o mesmo usado pelos SDKs):
        // POST /storage/v1/object/sign/{bucket}/{path}
        String body = MAPPER.writeValueAsString(Map.of("expiresIn", expiresInSeconds));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/storage/v1/object/sign/" + BUCKET + "/" + pathInBucket))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Falha ao criar signed URL: " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        String signed = null;
        signed = root.path("signedURL").asText(null);
        if (signed == null || signed.isBlank()) {
            signed = root.path("signedUrl").asText(null);
        }

        if (signed == null || signed.isBlank()) {
            throw new RuntimeException("Resposta inesperada ao criar signed URL: " + response.body());
        }

        String full;
        if (signed.startsWith("http")) {
            full = signed;
        } else if (signed.startsWith("/storage/v1/object/sign/")) {
            full = SUPABASE_URL + signed;
        } else {
            // Alguns retornos trazem apenas o querystring (?token=...), ou um path relativo.
            // Normaliza para o endpoint de GET do objeto assinado.
            String query = "";
            int q = signed.indexOf('?');
            if (q >= 0) {
                query = signed.substring(q);
            } else if (signed.startsWith("?")) {
                query = signed;
            }
            full = SUPABASE_URL + "/storage/v1/object/sign/" + BUCKET + "/" + pathInBucket + query;
        }

        Instant expiresAt = now.plusSeconds(expiresInSeconds).minus(SIGNED_URL_SAFETY_WINDOW);
        if (expiresAt.isBefore(now)) {
            expiresAt = now.plusSeconds(expiresInSeconds);
        }
        signedUrlCache.put(pathInBucket, new SignedUrlCacheEntry(full, expiresAt));

        return full;
    }
}
