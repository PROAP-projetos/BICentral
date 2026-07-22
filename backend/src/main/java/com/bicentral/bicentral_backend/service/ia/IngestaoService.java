package com.bicentral.bicentral_backend.service.ia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.ChunkDTO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IngestaoService {

    private final EmbeddingService embeddingService;

    @org.springframework.beans.factory.annotation.Value("${supabase.url}")
    private String supabaseUrl;

    @org.springframework.beans.factory.annotation.Value("${supabase.key}")
    private String supabaseKey;

    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public IngestaoService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    // Extrai todo o texto de um arquivo PDF e retorna como String
    public String extrairTextoPDF(String caminhoArquivo) throws IOException {
        File file = new File(caminhoArquivo);
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    // Extrai texto estruturado de um Excel
    public String extrairTextoExcel(String caminhoArquivo) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (InputStream is = new FileInputStream(new File(caminhoArquivo));
                Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            DataFormatter formatter = new DataFormatter();

            if (headerRow == null)
                return "";

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null)
                    continue;

                StringBuilder linhaNarrativa = new StringBuilder();

                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String cabecalho = formatter.formatCellValue(headerRow.getCell(j));
                    String valor = formatter.formatCellValue(currentRow.getCell(j));

                    if (!valor.trim().isEmpty()) {
                        linhaNarrativa.append("[")
                                .append(cabecalho)
                                .append(": ")
                                .append(valor)
                                .append("] ");
                    }
                }

                sb.append(linhaNarrativa).append("\n");
            }
        }

        return sb.toString();
    }

    // Extrai texto de arquivo TXT
    public String extrairTextoTXT(String caminhoArquivo) throws IOException {
        return Files.readString(Path.of(caminhoArquivo), StandardCharsets.UTF_8);
    }

    // Processa Excel em lotes com texto e metadata
    public List<ChunkDTO> processarPlanilhaExcelPorLotes(String caminhoArquivo, String equipe, String acesso,
            String nomeArquivo) throws IOException {
        List<ChunkDTO> chunksGerados = new ArrayList<>();
        String grupoId = java.util.UUID.randomUUID().toString();
        int tamanhoLote = 1; // antes tava 50 e agr 1 eita diacho

        try (InputStream is = new FileInputStream(new File(caminhoArquivo));
                Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            DataFormatter formatter = new DataFormatter();

            if (headerRow == null)
                return chunksGerados;

            StringBuilder loteTextoSemantico = new StringBuilder();
            List<Map<String, Object>> loteDadosEstruturados = new ArrayList<>();
            int contadorLinhasNoLote = 0;
            int totalLinhas = sheet.getLastRowNum();

            for (int i = 1; i <= totalLinhas; i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null)
                    continue;

                StringBuilder linhaNarrativa = new StringBuilder();
                Map<String, Object> linhaChaveValor = new HashMap<>();

                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String cabecalho = formatter.formatCellValue(headerRow.getCell(j));
                    Cell cell = currentRow.getCell(j);

                    if (cell == null || cabecalho.trim().isEmpty())
                        continue;

                    if (cell.getCellType() == CellType.NUMERIC) {
                        linhaChaveValor.put(cabecalho, cell.getNumericCellValue());
                    } else if (cell.getCellType() == CellType.BOOLEAN) {
                        linhaChaveValor.put(cabecalho, cell.getBooleanCellValue());
                    } else {
                        linhaChaveValor.put(cabecalho, formatter.formatCellValue(cell));
                    }

                    String valorTexto = formatter.formatCellValue(cell);
                    if (!valorTexto.trim().isEmpty()) {
                        linhaNarrativa.append(cabecalho)
                                .append(": ")
                                .append(valorTexto)
                                .append(", ");
                    }
                }

                if (!linhaChaveValor.isEmpty()) {
                    // Remove a última vírgula e o espaço
                    if (linhaNarrativa.length() > 2) {
                        linhaNarrativa.setLength(linhaNarrativa.length() - 2);
                    }

                    // Cria uma frase com semântica forte para o RAG
                    loteTextoSemantico.append("Dados da linha: ").append(linhaNarrativa).append(".\n");

                    loteDadosEstruturados.add(linhaChaveValor);
                    contadorLinhasNoLote++;
                }

                if (contadorLinhasNoLote == tamanhoLote) {
                    adicionarChunkPlanilha(chunksGerados, loteTextoSemantico, loteDadosEstruturados, equipe, acesso,
                            grupoId, nomeArquivo);
                    contadorLinhasNoLote = 0;
                }
            }

            if (!loteDadosEstruturados.isEmpty()) {
                adicionarChunkPlanilha(chunksGerados, loteTextoSemantico, loteDadosEstruturados, equipe, acesso,
                        grupoId, nomeArquivo);
            }
        }

        return chunksGerados;
    }

    private void adicionarChunkPlanilha(
            List<ChunkDTO> chunksGerados,
            StringBuilder loteTextoSemantico,
            List<Map<String, Object>> loteDadosEstruturados,
            String equipe,
            String acesso,
            String grupoId,
            String nomeArquivo) {
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("tipo", "tabular");
        metadataMap.put("nome_arquivo", nomeArquivo);
        metadataMap.put("equipe", equipe);
        metadataMap.put("grupo_id", grupoId);
        metadataMap.put("dados", new ArrayList<>(loteDadosEstruturados));

        chunksGerados.add(new ChunkDTO(
                loteTextoSemantico.toString().trim(),
                equipe,
                acesso,
                grupoId,
                nomeArquivo,
                metadataMap));

        loteTextoSemantico.setLength(0);
        loteDadosEstruturados.clear();
    }

    // Limpa o texto removendo ruídos como números de página, quebras de linha e
    // espaços excessivos
    public String limparTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isEmpty())
            return "";

        return textoBruto
                .replaceAll("(?m)^\\s*\\d+\\s*$", "")
                .replaceAll("(\\w+)-\\s*\\n\\s*(\\w+)", "$1$2")
                .replaceAll("(?<=\\w)\\s*\\n\\s*(?=\\w)", " ")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    // Divide o texto em pedaços menores (chunks) com sobreposição para preservar
    // contexto
    public List<String> fatiarTexto(String textoLimpo) {
        int tamanhoChunk = 512;
        int overlap = 64;

        String[] palavras = textoLimpo.split("\\s+");
        List<String> chunks = new ArrayList<>();

        if (palavras.length <= tamanhoChunk) {
            chunks.add(textoLimpo);
            return chunks;
        }

        for (int i = 0; i < palavras.length; i += (tamanhoChunk - overlap)) {
            StringBuilder chunkAtual = new StringBuilder();

            for (int j = i; j < i + tamanhoChunk && j < palavras.length; j++) {
                chunkAtual.append(palavras[j]).append(" ");
            }

            chunks.add(chunkAtual.toString().trim());

            if (i + tamanhoChunk >= palavras.length)
                break;
        }

        return chunks;
    }

    // Cria objetos ChunkDTO adicionando metadados (equipe, acesso, grupo e arquivo)
    public List<ChunkDTO> montarChunksComMetadados(List<String> chunks, String equipe, String acesso,
            String nomeArquivo) {
        List<ChunkDTO> listaParaSalvar = new ArrayList<>();
        String grupoId = java.util.UUID.randomUUID().toString();

        for (int i = 0; i < chunks.size(); i++) {
            listaParaSalvar.add(new ChunkDTO(
                    chunks.get(i),
                    equipe,
                    acesso,
                    grupoId,
                    nomeArquivo,
                    null));
        }

        return listaParaSalvar;
    }

    // Gera embeddings e salva os chunks no Supabase
    public void salvarNoBanco(List<String> chunks, String equipe, String acesso, String nomeArquivo, Long equipeId) {
        List<ChunkDTO> listaParaSalvar = montarChunksComMetadados(chunks, equipe, acesso, nomeArquivo);
        embeddingService.salvarChunks(listaParaSalvar, equipeId);
    }

    // Faz o upload do arquivo físico para o Bucket do Supabase Storage
    private String fazerUploadParaBucket(String caminhoArquivo, String nomeArquivo) throws Exception {
        // 1. Limpa o nome do arquivo para evitar erros de URL
        String nomeSeguro = nomeArquivo.replaceAll("\\s+", "_");

        // 2. Monta a URL do bucket (certifique-se de que o bucket chama
        // 'proiap-documentos')
        String urlUpload = supabaseUrl + "/storage/v1/object/proiap-documentos/" + nomeSeguro;

        // 3. Lê os bytes do arquivo local
        byte[] fileBytes = Files.readAllBytes(Path.of(caminhoArquivo));

        // 4. Define o tipo do arquivo básico
        String contentType = "application/octet-stream";
        if (nomeSeguro.toLowerCase().endsWith(".pdf"))
            contentType = "application/pdf";
        else if (nomeSeguro.toLowerCase().endsWith(".xlsx"))
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        // 5. Dispara para o Supabase
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(urlUpload))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", contentType)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        java.net.http.HttpResponse<String> response = HTTP.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ Arquivo salvo no Supabase Storage: " + nomeSeguro);
            return nomeSeguro;
        } else {
            System.err.println("❌ Erro ao salvar no bucket: " + response.body());
            throw new RuntimeException("Falha ao salvar no bucket: " + response.body());
        }
    }

    public List<ChunkDTO> executarIngestao(String caminhoArquivo, String nomeArquivo, String equipe, String acesso,
            Long equipeId) throws Exception {

        try {
            fazerUploadParaBucket(caminhoArquivo, nomeArquivo);
        } catch (Exception e) {
            System.err.println("Aviso: O arquivo não foi salvo no bucket. Motivo: " + e.getMessage());
        }

        String nomeArquivoNormalizado = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
        List<ChunkDTO> chunksProntos;

        if (nomeArquivoNormalizado.endsWith(".xlsx") || nomeArquivoNormalizado.endsWith(".xls")) {
            chunksProntos = processarPlanilhaExcelPorLotes(caminhoArquivo, equipe, acesso, nomeArquivo);
        } else {
            String textoBruto = nomeArquivoNormalizado.endsWith(".txt")
                    ? extrairTextoTXT(caminhoArquivo)
                    : extrairTextoPDF(caminhoArquivo);
            String textoLimpo = limparTexto(textoBruto);
            List<String> fatias = fatiarTexto(textoLimpo);
            chunksProntos = montarChunksComMetadados(fatias, equipe, acesso, nomeArquivo);
        }

        if (chunksProntos.isEmpty() || chunksProntos.stream().allMatch(chunk -> chunk.getConteudo().isBlank())) {
            return chunksProntos;
        }

        embeddingService.salvarChunks(chunksProntos, equipeId);
        return chunksProntos;
    }
}
