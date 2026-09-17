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
public class SincronizacaoPatJob {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // Pasta-ponte usada só pelo modo manual de 3 fases (config/fetch/load, ver
    // SincronizacaoUftRunner) — a API da UFT só é alcançável com a VPN da UFT ligada (GlobalProtect),
    // e o banco (Supabase) só é alcançável com ela desligada (Cloudflare WARP), então as duas
    // metades do job normal não cabem numa execução só quando rodado de uma máquina fora do Render.
    // Cada fase roda num processo Java separado, nunca ao mesmo tempo, e troca dado por arquivo.
    private static final Path PASTA_PONTE = Path.of("sync-data", "pat");

    public SincronizacaoPatJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // Best-effort: no modo manual de 3 fases (ver SincronizacaoUftApplication), a fase
        // "fetch" instancia esta classe sem banco disponível (só a VPN da UFT) — sem o
        // try/catch, essa checagem de tabela derrubaria o processo inteiro à toa, já que
        // "fetch" nem grava no banco (quem precisa da tabela existir é "load").
        try {
            garantirTabela();
        } catch (Exception e) {
            System.err.println(">>> JOB PAT: não foi possível confirmar/criar a tabela agora (ok se for a fase 'fetch'): " + e.getMessage());
        }
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

        int anoParaSalvar = anoDaUrl(url);

        try {
            System.out.println(">>> JOB PAT: Chamando API " + url);
            HttpResponse<String> response = chamarUft(url, token);

            String erroHttp = validarResposta(response);
            if (erroHttp != null) {
                registrarResultado("ERRO", erroHttp);
                return;
            }

            int processados = processarPat(response.body(), anoParaSalvar);

            registrarResultado("SUCESSO", processados + " registros de PAT salvos.");
            System.out.println(">>> JOB PAT: Concluído com sucesso.");

        } catch (Exception e) {
            registrarResultado("ERRO", detalheErro(e));
        }
    }

    // ---------------------------------------------------------------------------------------
    // FASE 1/3 (config) — roda com acesso ao banco (VPN da UFT desligada). Só copia a config
    // da API pra um arquivo local, porque a fase 2 não vai ter acesso ao banco pra buscar isso.
    // ---------------------------------------------------------------------------------------
    public void salvarConfiguracaoLocal() throws IOException {
        Files.createDirectories(PASTA_PONTE);
        Map<String, Object> config;
        try {
            config = jdbcTemplate.queryForMap("SELECT url, token, ativo FROM integracao_uft WHERE tipo_api = 'PAT'");
        } catch (Exception e) {
            System.err.println(">>> SYNC-UFT PAT (config): configuração não encontrada no banco.");
            return;
        }
        MAPPER.writeValue(PASTA_PONTE.resolve("config.json").toFile(), config);
        System.out.println(">>> SYNC-UFT PAT (config): salvo em " + PASTA_PONTE.resolve("config.json"));
    }

    // ---------------------------------------------------------------------------------------
    // FASE 2/3 (fetch) — roda com acesso à API da UFT (VPN da UFT ligada, sem banco). Lê a config
    // salva na fase 1, chama a API, e só GRAVA o resultado bruto em arquivo — não toca no banco.
    // ---------------------------------------------------------------------------------------
    public void buscarDaUftEsalvarLocal() throws IOException {
        Path arquivoConfig = PASTA_PONTE.resolve("config.json");
        if (!Files.exists(arquivoConfig)) {
            System.err.println(">>> SYNC-UFT PAT (fetch): config.json não existe — rode a fase 'config' primeiro.");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = MAPPER.readValue(arquivoConfig.toFile(), Map.class);
        Boolean ativo = (Boolean) config.get("ativo");
        if (ativo == null || !ativo) {
            System.out.println(">>> SYNC-UFT PAT (fetch): integração desativada. Pulando.");
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
            System.out.println(">>> SYNC-UFT PAT (fetch): chamando API " + url);
            HttpResponse<String> response = chamarUft(url, token);

            String erroHttp = validarResposta(response);
            if (erroHttp != null) {
                salvarResultadoLocal("ERRO", erroHttp, anoParaSalvar);
                return;
            }

            Files.writeString(PASTA_PONTE.resolve("payload.json"), response.body(), StandardCharsets.UTF_8);
            salvarResultadoLocal("SUCESSO", null, anoParaSalvar);
            System.out.println(">>> SYNC-UFT PAT (fetch): payload salvo, " + response.body().length() + " caractere(s).");

        } catch (Exception e) {
            salvarResultadoLocal("ERRO", detalheErro(e), anoParaSalvar);
        }
    }

    // ---------------------------------------------------------------------------------------
    // FASE 3/3 (load) — roda com acesso ao banco de novo (VPN da UFT desligada). Lê o que a fase
    // 2 salvou local e só AGORA grava no banco — inclusive o status/mensagem de erro, se algo deu
    // errado na fase 2, pra manter o mesmo histórico que o job de produção já registrava sozinho.
    // ---------------------------------------------------------------------------------------
    public void carregarNoBanco() throws Exception {
        Path arquivoResultado = PASTA_PONTE.resolve("resultado.json");
        if (!Files.exists(arquivoResultado)) {
            System.err.println(">>> SYNC-UFT PAT (load): resultado.json não existe — rode a fase 'fetch' primeiro.");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = MAPPER.readValue(arquivoResultado.toFile(), Map.class);
        String status = (String) resultado.get("status");
        int ano = ((Number) resultado.get("ano")).intValue();

        if ("PULADO".equals(status)) {
            System.out.println(">>> SYNC-UFT PAT (load): fase de busca foi pulada, nada a carregar.");
            return;
        }

        if ("ERRO".equals(status)) {
            registrarResultado("ERRO", (String) resultado.get("mensagem"));
            System.out.println(">>> SYNC-UFT PAT (load): erro registrado no banco (veio da fase de busca).");
            return;
        }

        Path arquivoPayload = PASTA_PONTE.resolve("payload.json");
        if (!Files.exists(arquivoPayload)) {
            registrarResultado("ERRO", "payload.json ausente apesar de status SUCESSO na fase de busca.");
            return;
        }

        String payload = Files.readString(arquivoPayload, StandardCharsets.UTF_8);
        int processados = processarPat(payload, ano);
        registrarResultado("SUCESSO", processados + " registros de PAT salvos.");
        System.out.println(">>> SYNC-UFT PAT (load): " + processados + " registro(s) carregado(s) no banco.");
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
        System.err.println(">>> JOB PAT ERRO: " + detalhe);
        e.printStackTrace();
        return detalhe;
    }

    // Lote de 500 em vez de um INSERT por linha — com milhares de registros, um insert por
    // vez vira milhares de idas-e-voltas de rede sequenciais, lento e frágil (qualquer soluço
    // de conexão no meio derruba tudo sem nada salvo). Em lote, a mesma carga vira umas poucas
    // dezenas de idas-e-voltas — bem mais rápido e muito menos exposto a essa trava.
    private static final int TAMANHO_LOTE = 500;

    private int processarPat(String jsonBody, int ano) throws Exception {
        List<Map<String, Object>> itens = MAPPER.readValue(jsonBody, new TypeReference<List<Map<String, Object>>>() {});

        String sql = """
            INSERT INTO pat_dados (id, departamento, ano, dados_completos, atualizado_em)
            VALUES (?, ?, ?, ?::jsonb, NOW())
            ON CONFLICT (id) DO UPDATE SET
                departamento = EXCLUDED.departamento,
                ano = EXCLUDED.ano,
                dados_completos = EXCLUDED.dados_completos,
                atualizado_em = NOW()
            """;

        List<Object[]> lote = new java.util.ArrayList<>(TAMANHO_LOTE);
        int processados = 0;

        for (Map<String, Object> item : itens) {
            try {
                // ATENÇÃO: aqui a chave é "id" minúsculo (no Tarefas é "ID" maiúsculo)
                Long id = Long.parseLong(String.valueOf(item.get("id")).trim());
                String departamento = String.valueOf(item.getOrDefault("Departamento", "")).trim();
                String jsonDoItem = MAPPER.writeValueAsString(item);
                lote.add(new Object[]{id, departamento, ano, jsonDoItem});
            } catch (Exception e) {
                System.err.println(">>> JOB PAT: falha ao preparar um item, pulando: " + e.getMessage());
                continue;
            }

            if (lote.size() >= TAMANHO_LOTE) {
                processados += executarLote(sql, lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) {
            processados += executarLote(sql, lote);
        }

        return processados;
    }

    private int executarLote(String sql, List<Object[]> lote) {
        try {
            jdbcTemplate.batchUpdate(sql, lote);
            return lote.size();
        } catch (Exception e) {
            System.err.println(">>> JOB PAT: falha ao gravar um lote de " + lote.size() + " item(ns): " + e.getMessage());
            return 0;
        }
    }

    private void registrarResultado(String status, String mensagem) {
        jdbcTemplate.update("""
            UPDATE integracao_uft
            SET ultima_execucao = NOW(), ultimo_status = ?, ultima_mensagem = ?
            WHERE tipo_api = 'PAT'
            """, status, mensagem);
    }
}
