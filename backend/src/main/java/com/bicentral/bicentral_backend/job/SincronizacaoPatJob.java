package com.bicentral.bicentral_backend.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "uft.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SincronizacaoPatJob {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public SincronizacaoPatJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabela();
    }

    private void garantirTabela() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS pat_dados (
                id BIGINT PRIMARY KEY,
                departamento TEXT,
                ano INT,
                dados_completos JSONB,
                atualizado_em TIMESTAMP
            )
            """);
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000L, initialDelay = 70 * 1000L)
    public void executar() {
        System.out.println(">>> JOB PAT: Iniciando sincronização...");

        Map<String, Object> config;
        try {
            config = jdbcTemplate.queryForMap("SELECT url, token, ativo FROM integracao_uft WHERE tipo_api = 'PAT'");
        } catch (Exception e) {
            System.err.println(">>> JOB PAT: Configuração não encontrada no banco.");
            return;
        }

        Boolean ativo = (Boolean) config.get("ativo");
        if (ativo == null || !ativo) {
            System.out.println(">>> JOB PAT: Integração desativada. Pulando.");
            return;
        }

        String url = (String) config.get("url");
        String token = (String) config.get("token");

        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            registrarResultado("ERRO", "URL ou token não configurados.");
            return;
        }

        int anoParaSalvar = LocalDate.now().getYear();
        Matcher matcherAno = Pattern.compile("ano=(\\d{4})").matcher(url);
        if (matcherAno.find()) {
            anoParaSalvar = Integer.parseInt(matcherAno.group(1));
        }

        try {
            System.out.println(">>> JOB PAT: Chamando API " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                registrarResultado("ERRO", "HTTP " + response.statusCode());
                return;
            }

            if (response.body().trim().matches("(?s)\\{\\s*\"erro\"\\s*:.*\\}")) {
                registrarResultado("ERRO", "API da UFT retornou erro interno.");
                return;
            }

            int processados = processarPat(response.body(), anoParaSalvar);

            registrarResultado("SUCESSO", processados + " registros de PAT salvos.");
            System.out.println(">>> JOB PAT: Concluído com sucesso.");

        } catch (Exception e) {
            String detalhe = e.getClass().getSimpleName() + ": " + e.getMessage()
                    + (e.getCause() != null ? " | causa: " + e.getCause() : "");
            System.err.println(">>> JOB PAT ERRO: " + detalhe);
            e.printStackTrace();
            registrarResultado("ERRO", detalhe);
        }
    }

    private int processarPat(String jsonBody, int ano) throws Exception {
        List<Map<String, Object>> itens = MAPPER.readValue(jsonBody, new TypeReference<List<Map<String, Object>>>() {});
        int processados = 0;

        for (Map<String, Object> item : itens) {
            try {
                // ATENÇÃO: aqui a chave é "id" minúsculo (no Tarefas é "ID" maiúsculo)
                Long id = Long.parseLong(String.valueOf(item.get("id")).trim());
                String departamento = String.valueOf(item.getOrDefault("Departamento", "")).trim();
                String jsonDoItem = MAPPER.writeValueAsString(item);

                jdbcTemplate.update("""
                    INSERT INTO pat_dados (id, departamento, ano, dados_completos, atualizado_em)
                    VALUES (?, ?, ?, ?::jsonb, NOW())
                    ON CONFLICT (id) DO UPDATE SET
                        departamento = EXCLUDED.departamento,
                        ano = EXCLUDED.ano,
                        dados_completos = EXCLUDED.dados_completos,
                        atualizado_em = NOW()
                    """, id, departamento, ano, jsonDoItem);

                processados++;
            } catch (Exception e) {
                System.err.println(">>> JOB PAT: falha ao processar um item, pulando.");
            }
        }

        return processados;
    }

    private void registrarResultado(String status, String mensagem) {
        jdbcTemplate.update("""
            UPDATE integracao_uft
            SET ultima_execucao = NOW(), ultimo_status = ?, ultima_mensagem = ?
            WHERE tipo_api = 'PAT'
            """, status, mensagem);
    }
}