package com.bicentral.bicentral_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.http.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String SUPABASE_URL;

    @Value("${supabase.key}")
    private String SUPABASE_KEY;

    @Value("${supabase.bucket}")
    private String BUCKET;

    private String getApiEndpoint() {
        return SUPABASE_URL + "/storage/v1/object/";
    }

    public String uploadFile(String pathInBucket, Path localFilePath) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        byte[] fileBytes = Files.readAllBytes(localFilePath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiEndpoint() + BUCKET + "/" + pathInBucket))
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "image/png")
                .header("x-upsert", "true")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + pathInBucket;
        } else {
            throw new RuntimeException("Falha no upload: " + response.body());
        }
    }

    public void deleteFile(String pathInBucket) throws Exception {
        if (pathInBucket == null || pathInBucket.isBlank()) {
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiEndpoint() + BUCKET + "/" + pathInBucket))
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Falha no delete: " + response.body());
        }
    }
}
