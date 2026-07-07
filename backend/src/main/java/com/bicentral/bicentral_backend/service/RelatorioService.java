package com.bicentral.bicentral_backend.service;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RelatorioService {

    private final JdbcTemplate jdbcTemplate;
    private final AgenteRelatorio agenteRelatorio;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public RelatorioService(JdbcTemplate jdbcTemplate, AgenteRelatorio agenteRelatorio) {
        this.jdbcTemplate = jdbcTemplate;
        this.agenteRelatorio = agenteRelatorio;
    }

    public Long solicitarRelatorio(Long usuarioId, String departamento, String tipo) {
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO relatorios_gerados (usuario_id, departamento, tipo, status)
            VALUES (?, ?, ?, 'PROCESSANDO')
            RETURNING id
            """, Long.class, usuarioId, departamento, tipo);

        System.out.println(">>> RELATORIO solicitado: id=" + id + ", departamento=" + departamento + ", tipo=" + tipo);
        processarRelatorioAsync(id, departamento, tipo);
        return id;
    }

    public Map<String, Object> buscarStatus(Long id) {
        return jdbcTemplate.queryForMap(
            "SELECT id, status, arquivo_url, mensagem_erro, departamento, tipo, criado_em, concluido_em FROM relatorios_gerados WHERE id = ?",
            id
        );
    }

    @Async
    public void processarRelatorioAsync(Long id, String departamento, String tipo) {
        try {
            System.out.println(">>> RELATORIO processando id=" + id);

            String dadosColetados = coletarDados(departamento, tipo);
            String textoRelatorio = agenteRelatorio.gerarTextoRelatorio(dadosColetados);

            byte[] docxBytes = gerarDocx(departamento, tipo, textoRelatorio);
            String urlArquivo = enviarParaBucket(docxBytes, departamento, id);

            jdbcTemplate.update("""
                UPDATE relatorios_gerados
                SET status = 'PRONTO', arquivo_url = ?, concluido_em = NOW()
                WHERE id = ?
                """, urlArquivo, id);

            System.out.println(">>> RELATORIO concluído id=" + id + ", url=" + urlArquivo);

        } catch (Exception e) {
            System.err.println(">>> RELATORIO ERRO id=" + id + ": " + e.getMessage());
            e.printStackTrace();
            jdbcTemplate.update("""
                UPDATE relatorios_gerados
                SET status = 'ERRO', mensagem_erro = ?
                WHERE id = ?
                """, e.getMessage(), id);
        }
    }

    private String coletarDados(String departamento, String tipo) {
        StringBuilder sb = new StringBuilder();
        sb.append("DEPARTAMENTO: ").append(departamento).append("\n");
        sb.append("TIPO DE PLANO: ").append(tipo).append("\n\n");

        if ("PAT".equalsIgnoreCase(tipo)) {
            Map<String, Object> resumo = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total_acoes,
                    ROUND(AVG(percentual_execucao) * 100, 2) AS media_geral,
                    COUNT(*) FILTER (WHERE percentual_execucao = 0) AS zeradas,
                    COUNT(*) FILTER (WHERE percentual_execucao > 0 AND percentual_execucao < 1) AS em_andamento,
                    COUNT(*) FILTER (WHERE percentual_execucao >= 1) AS concluidas
                FROM pat_execucao_departamento
                WHERE departamento ILIKE ?
                """, "%" + departamento.trim() + "%");

            sb.append("RESUMO GERAL PAT:\n").append(resumo).append("\n\n");

            List<Map<String, Object>> piores = jdbcTemplate.queryForList("""
                SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual
                FROM pat_execucao_departamento
                WHERE departamento ILIKE ?
                ORDER BY percentual_execucao ASC
                LIMIT 15
                """, "%" + departamento.trim() + "%");
            sb.append("AÇÕES COM MENOR EXECUÇÃO:\n");
            piores.forEach(row -> sb.append("- ").append(row.get("titulo_acao")).append(": ").append(row.get("percentual")).append("%\n"));

            List<Map<String, Object>> melhores = jdbcTemplate.queryForList("""
                SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual
                FROM pat_execucao_departamento
                WHERE departamento ILIKE ?
                ORDER BY percentual_execucao DESC
                LIMIT 15
                """, "%" + departamento.trim() + "%");
            sb.append("\nAÇÕES COM MAIOR EXECUÇÃO:\n");
            melhores.forEach(row -> sb.append("- ").append(row.get("titulo_acao")).append(": ").append(row.get("percentual")).append("%\n"));

        } else if ("PDI".equalsIgnoreCase(tipo)) {
            List<Map<String, Object>> acoes = jdbcTemplate.queryForList("""
                SELECT codigo, titulo, ROUND(percentual_pdi, 2) AS percentual
                FROM acoes_pdi
                WHERE estrutura = 'Ação' AND departamentos ILIKE ?
                ORDER BY percentual_pdi ASC
                """, "%" + departamento.trim() + "%");
            sb.append("AÇÕES DO PDI (ordenadas da menor para a maior execução acumulada):\n");
            acoes.forEach(row -> sb.append("- [").append(row.get("codigo")).append("] ").append(row.get("titulo")).append(": ").append(row.get("percentual")).append("%\n"));

        } else if ("COMPARATIVO".equalsIgnoreCase(tipo)) {
            sb.append(coletarDados(departamento, "PDI"));
            sb.append("\n---\n\n");
            sb.append(coletarDados(departamento, "PAT"));
        }

        return sb.toString();
    }

    private byte[] gerarDocx(String departamento, String tipo, String textoRelatorio) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {

            XWPFParagraph titulo = document.createParagraph();
            titulo.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tituloRun = titulo.createRun();
            tituloRun.setText("Relatório de Desempenho - " + departamento);
            tituloRun.setBold(true);
            tituloRun.setFontSize(16);

            XWPFParagraph subtitulo = document.createParagraph();
            subtitulo.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subtituloRun = subtitulo.createRun();
            subtituloRun.setText("Plano: " + tipo + " | Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            subtituloRun.setItalic(true);
            subtituloRun.setFontSize(10);

            document.createParagraph();

            String[] linhas = textoRelatorio.split("\\r?\\n");
            for (String linha : linhas) {
                if (linha.trim().isEmpty()) continue;

                XWPFParagraph paragrafo = document.createParagraph();
                XWPFRun run = paragrafo.createRun();

                String linhaLimpa = linha.replaceAll("^#+\\s*", "").replaceAll("\\*\\*(.+?)\\*\\*", "$1");
                boolean pareceTitulo = linha.trim().startsWith("#") || linha.matches("^\\d+\\.\\s+.+");

                run.setText(linhaLimpa);
                if (pareceTitulo) {
                    run.setBold(true);
                    run.setFontSize(13);
                    paragrafo.setSpacingBefore(200);
                } else {
                    run.setFontSize(11);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private String enviarParaBucket(byte[] docxBytes, String departamento, Long relatorioId) throws Exception {
        String nomeSeguro = "relatorio_" + departamento.replaceAll("[^a-zA-Z0-9]", "_") + "_" + relatorioId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".docx";
        String urlUpload = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeSeguro;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlUpload))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .POST(HttpRequest.BodyPublishers.ofByteArray(docxBytes))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return gerarUrlAssinada(nomeSeguro);
        } else {
            throw new RuntimeException("Falha ao enviar relatório para o bucket: " + response.body());
        }
    }

    /**
     * Gera uma URL assinada (temporária) para o arquivo, funcionando mesmo se o bucket
     * "proiap-documentos" não estiver configurado como público no Supabase.
     * Válida por 7 dias (604800 segundos).
     */
    private String gerarUrlAssinada(String nomeArquivo) throws Exception {
        String urlSign = supabaseUrl + "/storage/v1/object/sign/proiap-documentos/" + nomeArquivo;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlSign))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expiresIn\": 604800}"))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> json = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.body(), Map.class);
            String signedPath = (String) json.get("signedURL");
            if (signedPath == null) {
                throw new RuntimeException("Resposta do Supabase sem signedURL: " + response.body());
            }
            return supabaseUrl + "/storage/v1" + signedPath;
        } else {
            throw new RuntimeException("Falha ao gerar URL assinada: " + response.body());
        }
    }
}