package com.bicentral.bicentral_backend.controller.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @GetMapping("/view/{nomeArquivo}")
    public ResponseEntity<byte[]> visualizarDocumento(@PathVariable String nomeArquivo) {
        try {
            String nomeSeguro = nomeArquivo.replaceAll("\\s+", "_");

            String url = supabaseUrl + "/storage/v1/object/authenticated/proiap-documentos/" + nomeSeguro;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDisposition(ContentDisposition.inline().filename(nomeArquivo).build());

                return new ResponseEntity<>(response.body(), headers, HttpStatus.OK);
            } else {
                System.out
                        .println("❌ Erro no Supabase: " + response.statusCode() + " - " + new String(response.body()));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}