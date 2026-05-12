package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestaoService {

    private final EmbeddingService embeddingService;

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

    // Lê um arquivo Excel (.xlsx) e transforma cada linha em texto estruturado "[Coluna: Valor]"
    public String extrairTextoExcel(String caminhoArquivo) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (InputStream is = new FileInputStream(new File(caminhoArquivo));
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            DataFormatter formatter = new DataFormatter();

            if (headerRow == null) return "";

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;

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

    // Limpa o texto removendo ruídos como números de página, quebras de linha e espaços excessivos
    public String limparTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isEmpty()) return "";

        return textoBruto
                .replaceAll("(?m)^\\s*\\d+\\s*$", "")
                .replaceAll("(\\w+)-\\s*\\n\\s*(\\w+)", "$1$2")
                .replaceAll("(?<=\\w)\\s*\\n\\s*(?=\\w)", " ")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    // Divide o texto em pedaços menores (chunks) com sobreposição para preservar contexto
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

            if (i + tamanhoChunk >= palavras.length) break;
        }

        return chunks;
    }

    // Cria objetos ChunkDTO adicionando metadados (equipe, acesso, grupo e arquivo)
    public List<ChunkDTO> montarChunksComMetadados(List<String> chunks, String equipe, String acesso, String nomeArquivo) {
        List<ChunkDTO> listaParaSalvar = new ArrayList<>();
        String grupoId = java.util.UUID.randomUUID().toString();

        for (int i = 0; i < chunks.size(); i++) {
            listaParaSalvar.add(new ChunkDTO(
                    chunks.get(i),
                    equipe,
                    acesso,
                    grupoId,
                    nomeArquivo
            ));
        }

        return listaParaSalvar;
    }

    // Gera embeddings e salva os chunks no Supabase
    public void salvarNoBanco(List<String> chunks, String equipe, String acesso, String nomeArquivo, Long equipeId) {
        List<ChunkDTO> listaParaSalvar = montarChunksComMetadados(chunks, equipe, acesso, nomeArquivo);
        embeddingService.salvarChunks(listaParaSalvar, equipeId);
    }
}