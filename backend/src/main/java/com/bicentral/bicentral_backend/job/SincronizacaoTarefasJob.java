package com.bicentral.bicentral_backend.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "uft.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SincronizacaoTarefasJob {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // Ver comentário equivalente em SincronizacaoPatJob — mesma pasta-ponte, mesma razão.
    private static final Path PASTA_PONTE = Path.of("sync-data", "tarefas");

    public SincronizacaoTarefasJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // Best-effort, idempotente (ALTER TYPE pro mesmo tipo não faz nada) — a coluna
        // "departamento" foi criada como VARCHAR(100) em algum momento anterior, mas nomes
        // reais de departamento da UFT passam disso (ex: nomes de coordenação de pós-graduação
        // compostos), o que abortava o lote inteiro no INSERT com "value too long". Igual aos
        // outros "garantir" desta classe, não pode travar o processo se o banco não estiver
        // acessível agora (ex: fase "fetch" do modo manual de sincronização).
        try {
            jdbcTemplate.execute("ALTER TABLE pat_tarefas ALTER COLUMN departamento TYPE TEXT");
        } catch (Exception e) {
            System.err.println(">>> JOB TAREFAS: não foi possível ajustar a coluna 'departamento' agora: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void executar() {
        System.out.println(">>> JOB TAREFAS: Iniciando sincronização...");

        Map<String, Object> config;
        try {
            config = jdbcTemplate.queryForMap("SELECT url, token, ativo FROM integracao_uft WHERE tipo_api = 'TAREFAS'");
        } catch (Exception e) {
            System.err.println(">>> JOB TAREFAS: Configuração não encontrada no banco.");
            return;
        }

        Boolean ativo = (Boolean) config.get("ativo");
        if (ativo == null || !ativo) {
            System.out.println(">>> JOB TAREFAS: Integração desativada. Pulando.");
            return;
        }

        String url = (String) config.get("url");
        String token = (String) config.get("token");

        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            registrarResultado("ERRO", "URL ou token não configurados.");
            return;
        }

        int anoParaSalvar = anoDaUrl(url);

        try {
            System.out.println(">>> JOB TAREFAS: Chamando API " + url);
            HttpResponse<String> response = chamarUft(url, token);

            String erroHttp = validarResposta(response);
            if (erroHttp != null) {
                registrarResultado("ERRO", erroHttp);
                return;
            }

            int tarefasProcessadas = processarTarefas(response.body(), anoParaSalvar);

            registrarResultado("SUCESSO", tarefasProcessadas + " tarefas salvas.");
            System.out.println(">>> JOB TAREFAS: Concluído com sucesso.");

        } catch (Exception e) {
            registrarResultado("ERRO", detalheErro(e));
        }
    }

    // Ver comentários das 3 fases em SincronizacaoPatJob — mesmo desenho, mesma razão.

    public void salvarConfiguracaoLocal() throws IOException {
        Files.createDirectories(PASTA_PONTE);
        Map<String, Object> config;
        try {
            config = jdbcTemplate.queryForMap("SELECT url, token, ativo FROM integracao_uft WHERE tipo_api = 'TAREFAS'");
        } catch (Exception e) {
            System.err.println(">>> SYNC-UFT TAREFAS (config): configuração não encontrada no banco.");
            return;
        }
        MAPPER.writeValue(PASTA_PONTE.resolve("config.json").toFile(), config);
        System.out.println(">>> SYNC-UFT TAREFAS (config): salvo em " + PASTA_PONTE.resolve("config.json"));
    }

    public void buscarDaUftEsalvarLocal() throws IOException {
        Path arquivoConfig = PASTA_PONTE.resolve("config.json");
        if (!Files.exists(arquivoConfig)) {
            System.err.println(">>> SYNC-UFT TAREFAS (fetch): config.json não existe — rode a fase 'config' primeiro.");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = MAPPER.readValue(arquivoConfig.toFile(), Map.class);
        Boolean ativo = (Boolean) config.get("ativo");
        if (ativo == null || !ativo) {
            System.out.println(">>> SYNC-UFT TAREFAS (fetch): integração desativada. Pulando.");
            salvarResultadoLocal("PULADO", "Integração desativada.", 0);
            return;
        }

        String url = (String) config.get("url");
        String token = (String) config.get("token");
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            salvarResultadoLocal("ERRO", "URL ou token não configurados.", 0);
            return;
        }

        int anoParaSalvar = anoDaUrl(url);

        try {
            System.out.println(">>> SYNC-UFT TAREFAS (fetch): chamando API " + url);
            HttpResponse<String> response = chamarUft(url, token);

            String erroHttp = validarResposta(response);
            if (erroHttp != null) {
                salvarResultadoLocal("ERRO", erroHttp, anoParaSalvar);
                return;
            }

            Files.writeString(PASTA_PONTE.resolve("payload.json"), response.body(), StandardCharsets.UTF_8);
            salvarResultadoLocal("SUCESSO", null, anoParaSalvar);
            System.out.println(">>> SYNC-UFT TAREFAS (fetch): payload salvo, " + response.body().length() + " caractere(s).");

        } catch (Exception e) {
            salvarResultadoLocal("ERRO", detalheErro(e), anoParaSalvar);
        }
    }

    public void carregarNoBanco() throws Exception {
        Path arquivoResultado = PASTA_PONTE.resolve("resultado.json");
        if (!Files.exists(arquivoResultado)) {
            System.err.println(">>> SYNC-UFT TAREFAS (load): resultado.json não existe — rode a fase 'fetch' primeiro.");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = MAPPER.readValue(arquivoResultado.toFile(), Map.class);
        String status = (String) resultado.get("status");
        int ano = ((Number) resultado.get("ano")).intValue();

        if ("PULADO".equals(status)) {
            System.out.println(">>> SYNC-UFT TAREFAS (load): fase de busca foi pulada, nada a carregar.");
            return;
        }

        if ("ERRO".equals(status)) {
            registrarResultado("ERRO", (String) resultado.get("mensagem"));
            System.out.println(">>> SYNC-UFT TAREFAS (load): erro registrado no banco (veio da fase de busca).");
            return;
        }

        Path arquivoPayload = PASTA_PONTE.resolve("payload.json");
        if (!Files.exists(arquivoPayload)) {
            registrarResultado("ERRO", "payload.json ausente apesar de status SUCESSO na fase de busca.");
            return;
        }

        String payload = Files.readString(arquivoPayload, StandardCharsets.UTF_8);
        int processadas = processarTarefas(payload, ano);
        registrarResultado("SUCESSO", processadas + " tarefas salvas.");
        System.out.println(">>> SYNC-UFT TAREFAS (load): " + processadas + " tarefa(s) carregada(s) no banco.");
    }

    private void salvarResultadoLocal(String status, String mensagem, int ano) throws IOException {
        MAPPER.writeValue(PASTA_PONTE.resolve("resultado.json").toFile(),
                Map.of("status", status, "mensagem", mensagem == null ? "" : mensagem, "ano", ano));
    }

    private HttpResponse<String> chamarUft(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String validarResposta(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "HTTP " + response.statusCode();
        }
        if (response.body().trim().matches("(?s)\\{\\s*\"erro\"\\s*:.*\\}")) {
            return "API da UFT retornou erro interno.";
        }
        return null;
    }

    private int anoDaUrl(String url) {
        int anoParaSalvar = LocalDate.now().getYear();
        Matcher matcherAno = Pattern.compile("ano=(\\d{4})").matcher(url);
        if (matcherAno.find()) {
            anoParaSalvar = Integer.parseInt(matcherAno.group(1));
        }
        return anoParaSalvar;
    }

    private String detalheErro(Exception e) {
        String detalhe = e.getClass().getSimpleName() + ": " + e.getMessage()
                + (e.getCause() != null ? " | causa: " + e.getCause() : "");
        System.err.println(">>> JOB TAREFAS ERRO: " + detalhe);
        e.printStackTrace();
        return detalhe;
    }

    // Ver comentário equivalente em SincronizacaoPatJob — mesmo motivo (lote em vez de um
    // INSERT por linha).
    private static final int TAMANHO_LOTE = 500;

    private int processarTarefas(String jsonBody, int ano) throws Exception {
        List<Map<String, Object>> tarefas = MAPPER.readValue(jsonBody, new TypeReference<List<Map<String, Object>>>() {});

        String sql = """
            INSERT INTO pat_tarefas (id, departamento, ano, dados_completos, atualizado_em)
            VALUES (?, ?, ?, ?::jsonb, NOW())
            ON CONFLICT (id) DO UPDATE SET
                departamento = EXCLUDED.departamento,
                ano = EXCLUDED.ano,
                dados_completos = EXCLUDED.dados_completos,
                atualizado_em = NOW()
            """;

        List<Object[]> lote = new java.util.ArrayList<>(TAMANHO_LOTE);
        int processadas = 0;

        for (Map<String, Object> tarefa : tarefas) {
            try {
                Long id = Long.parseLong(String.valueOf(tarefa.get("ID")).trim());
                String departamento = String.valueOf(tarefa.getOrDefault("Departamento", "")).trim();
                String jsonDaTarefa = MAPPER.writeValueAsString(tarefa);
                lote.add(new Object[]{id, departamento, ano, jsonDaTarefa});
            } catch (Exception e) {
                System.err.println(">>> JOB TAREFAS: falha ao preparar uma tarefa, pulando: " + e.getMessage());
                continue;
            }

            if (lote.size() >= TAMANHO_LOTE) {
                processadas += executarLote(sql, lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) {
            processadas += executarLote(sql, lote);
        }

        return processadas;
    }

    private int executarLote(String sql, List<Object[]> lote) {
        try {
            jdbcTemplate.batchUpdate(sql, lote);
            return lote.size();
        } catch (Exception e) {
            System.err.println(">>> JOB TAREFAS: falha ao gravar um lote de " + lote.size() + " tarefa(s): " + e.getMessage());
            return 0;
        }
    }

    private void registrarResultado(String status, String mensagem) {
        jdbcTemplate.update("""
            UPDATE integracao_uft
            SET ultima_execucao = NOW(), ultimo_status = ?, ultima_mensagem = ?
            WHERE tipo_api = 'TAREFAS'
            """, status, mensagem);
    }
}
