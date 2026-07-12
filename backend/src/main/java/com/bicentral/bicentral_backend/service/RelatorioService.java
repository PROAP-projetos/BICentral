package com.bicentral.bicentral_backend.service;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    @PostConstruct
    public void prepararTabelaRelatorios() {
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS formato VARCHAR(10) DEFAULT 'DOCX'");
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS texto_relatorio TEXT");
        jdbcTemplate.execute("ALTER TABLE relatorios_gerados ADD COLUMN IF NOT EXISTS pdf_url TEXT");
    }

    public Long solicitarRelatorio(Long usuarioId, String departamento, String tipo) {
        return solicitarRelatorio(usuarioId, departamento, tipo, "DOCX");
    }

    public Long solicitarRelatorio(Long usuarioId, String departamento, String tipo, String formato) {
        String formatoFinal = normalizarFormato(formato);
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO relatorios_gerados (usuario_id, departamento, tipo, formato, status)
            VALUES (?, ?, ?, ?, 'PROCESSANDO')
            RETURNING id
            """, Long.class, usuarioId, departamento, tipo, formatoFinal);

        System.out.println(">>> RELATORIO solicitado: id=" + id + ", departamento=" + departamento + ", tipo=" + tipo + ", formato=" + formatoFinal);
        processarRelatorioAsync(id, departamento, tipo, formatoFinal);
        return id;
    }

    public Map<String, Object> buscarStatus(Long id) {
        return jdbcTemplate.queryForMap(
            "SELECT id, status, arquivo_url, mensagem_erro, departamento, tipo, COALESCE(formato, 'DOCX') AS formato, criado_em, concluido_em FROM relatorios_gerados WHERE id = ?",
            id
        );
    }

    public boolean excluirRelatorio(Long id, Long usuarioId) {
        List<Map<String, Object>> encontrados = jdbcTemplate.queryForList("""
            SELECT arquivo_url, pdf_url
            FROM relatorios_gerados
            WHERE id = ? AND usuario_id = ?
            """, id, usuarioId);

        if (encontrados.isEmpty()) {
            return false;
        }

        String arquivoUrl = (String) encontrados.get(0).get("arquivo_url");
        String nomeArquivo = extrairNomeArquivoDoBucket(arquivoUrl);
        if (nomeArquivo != null) {
            try {
                excluirArquivoDoBucket(nomeArquivo);
            } catch (Exception e) {
                System.err.println(">>> RELATORIO aviso: falha ao excluir arquivo do bucket id=" + id + ": " + e.getMessage());
            }
        }

        String pdfUrl = (String) encontrados.get(0).get("pdf_url");
        String nomePdf = extrairNomeArquivoDoBucket(pdfUrl);
        if (nomePdf != null) {
            try {
                excluirArquivoDoBucket(nomePdf);
            } catch (Exception e) {
                System.err.println(">>> RELATORIO aviso: falha ao excluir PDF do bucket id=" + id + ": " + e.getMessage());
            }
        }

        jdbcTemplate.update("DELETE FROM relatorios_gerados WHERE id = ? AND usuario_id = ?", id, usuarioId);
        return true;
    }

    public Map<String, Object> gerarOuBuscarPdf(Long id, Long usuarioId) {
        List<Map<String, Object>> encontrados = jdbcTemplate.queryForList("""
            SELECT id, departamento, tipo, status, texto_relatorio, pdf_url
            FROM relatorios_gerados
            WHERE id = ? AND usuario_id = ?
            """, id, usuarioId);

        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("Relatório não encontrado.");
        }

        Map<String, Object> relatorio = encontrados.get(0);
        if (!"PRONTO".equals(relatorio.get("status"))) {
            throw new IllegalStateException("Relatório ainda não está pronto.");
        }

        String pdfUrl = (String) relatorio.get("pdf_url");
        if (pdfUrl != null && !pdfUrl.isBlank()) {
            return Map.of("pdf_url", pdfUrl);
        }

        String textoRelatorio = (String) relatorio.get("texto_relatorio");
        if (textoRelatorio == null || textoRelatorio.isBlank()) {
            throw new IllegalStateException("Este relatório não possui texto armazenado para gerar PDF.");
        }

        try {
            String departamento = (String) relatorio.get("departamento");
            String tipo = (String) relatorio.get("tipo");
            byte[] pdfBytes = gerarPdf(departamento, tipo, textoRelatorio);
            String novaPdfUrl = enviarParaBucket(pdfBytes, departamento, id, "pdf", "application/pdf");

            jdbcTemplate.update("UPDATE relatorios_gerados SET pdf_url = ? WHERE id = ? AND usuario_id = ?", novaPdfUrl, id, usuarioId);
            return Map.of("pdf_url", novaPdfUrl);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar PDF do relatório: " + e.getMessage(), e);
        }
    }

    @Async
    public void processarRelatorioAsync(Long id, String departamento, String tipo, String formato) {
        try {
            System.out.println(">>> RELATORIO processando id=" + id);

            String dadosColetados = coletarDados(departamento, tipo);
            String textoRelatorio = agenteRelatorio.gerarTextoRelatorio(dadosColetados);

            byte[] arquivoBytes;
            String extensao;
            String contentType;
            if ("PDF".equalsIgnoreCase(formato)) {
                arquivoBytes = gerarPdf(departamento, tipo, textoRelatorio);
                extensao = "pdf";
                contentType = "application/pdf";
            } else {
                arquivoBytes = gerarDocx(departamento, tipo, textoRelatorio);
                extensao = "docx";
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            String urlArquivo = enviarParaBucket(arquivoBytes, departamento, id, extensao, contentType);

            jdbcTemplate.update("""
                UPDATE relatorios_gerados
                SET status = 'PRONTO', arquivo_url = ?, texto_relatorio = ?, formato = ?, concluido_em = NOW()
                WHERE id = ?
                """, urlArquivo, textoRelatorio, normalizarFormato(formato), id);

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

    private String normalizarFormato(String formato) {
        if (formato == null || formato.isBlank()) {
            return "DOCX";
        }
        return "PDF".equalsIgnoreCase(formato.trim()) ? "PDF" : "DOCX";
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

    private byte[] gerarPdf(String departamento, String tipo, String textoRelatorio) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margem = 56;
            float y = page.getMediaBox().getHeight() - margem;
            PDPageContentStream content = new PDPageContentStream(document, page);

            content.setFont(PDType1Font.HELVETICA_BOLD, 15);
            y = escreverLinhaPdf(content, "Relatorio de Desempenho - " + normalizarTextoPdf(departamento), margem, y, 18);

            content.setFont(PDType1Font.HELVETICA_OBLIQUE, 10);
            y = escreverLinhaPdf(content, "Plano: " + normalizarTextoPdf(tipo) + " | Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), margem, y, 24);

            for (String linha : textoRelatorio.split("\\r?\\n")) {
                if (linha.trim().isEmpty()) {
                    y -= 8;
                    continue;
                }

                boolean pareceTitulo = linha.trim().startsWith("#") || linha.matches("^\\d+\\.\\s+.+");
                String linhaLimpa = normalizarTextoPdf(linha.replaceAll("^#+\\s*", "").replaceAll("\\*\\*(.+?)\\*\\*", "$1"));
                content.setFont(pareceTitulo ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, pareceTitulo ? 12 : 10);
                float leading = pareceTitulo ? 16 : 14;

                for (String trecho : quebrarTextoPdf(linhaLimpa, pareceTitulo ? 82 : 96)) {
                    if (y < margem) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        content.setFont(pareceTitulo ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, pareceTitulo ? 12 : 10);
                        y = page.getMediaBox().getHeight() - margem;
                    }

                    y = escreverLinhaPdf(content, trecho, margem, y, leading);
                }

                y -= pareceTitulo ? 5 : 2;
            }

            content.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private String normalizarTextoPdf(String texto) {
        return texto == null ? "" : texto.replaceAll("[^\\r\\n\\t\\x20-\\xFF]", "-");
    }

    private List<String> quebrarTextoPdf(String texto, int limite) {
        java.util.ArrayList<String> linhas = new java.util.ArrayList<>();
        StringBuilder atual = new StringBuilder();

        for (String palavra : texto.split("\\s+")) {
            if (atual.length() + palavra.length() + 1 > limite && atual.length() > 0) {
                linhas.add(atual.toString());
                atual.setLength(0);
            }
            if (atual.length() > 0) atual.append(' ');
            atual.append(palavra);
        }

        if (atual.length() > 0) linhas.add(atual.toString());
        return linhas.isEmpty() ? List.of("") : linhas;
    }

    private float escreverLinhaPdf(PDPageContentStream content, String texto, float x, float y, float leading) throws Exception {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(texto);
        content.endText();
        return y - leading;
    }

    private String enviarParaBucket(byte[] arquivoBytes, String departamento, Long relatorioId, String extensao, String contentType) throws Exception {
        String nomeSeguro = "relatorio_" + departamento.replaceAll("[^a-zA-Z0-9]", "_") + "_" + relatorioId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extensao;
        String urlUpload = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeSeguro;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlUpload))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(arquivoBytes))
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

    private String extrairNomeArquivoDoBucket(String arquivoUrl) {
        if (arquivoUrl == null || arquivoUrl.isBlank()) {
            return null;
        }

        String[] marcadores = {
            "/storage/v1/object/sign/proiap-documentos/",
            "/storage/v1/object/proiap-documentos/"
        };

        for (String marcador : marcadores) {
            int inicio = arquivoUrl.indexOf(marcador);
            if (inicio < 0) continue;

            String nome = arquivoUrl.substring(inicio + marcador.length());
            int queryIndex = nome.indexOf('?');
            if (queryIndex >= 0) {
                nome = nome.substring(0, queryIndex);
            }
            return URLDecoder.decode(nome, StandardCharsets.UTF_8);
        }

        return null;
    }

    private void excluirArquivoDoBucket(String nomeArquivo) throws Exception {
        String urlDelete = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeArquivo;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlDelete))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .DELETE()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(response.body());
        }
    }
}
