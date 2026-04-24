package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

    // 1. Extração Bruta
    public String extrairTextoPDF(String caminhoArquivo) throws IOException {
        File file = new File(caminhoArquivo);
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    // Método para extrair texto de um arquivo Excel (.xlsx)
    public String extrairTextoExcel(String caminhoArquivo) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (InputStream is = new FileInputStream(new File(caminhoArquivo));
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0); // Assume que a primeira linha é o cabeçalho
            DataFormatter formatter = new DataFormatter();

            if (headerRow == null) return "";

            // Percorre as linhas (começando da 1, já que a 0 é cabeçalho)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;

                StringBuilder linhaNarrativa = new StringBuilder();

                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String cabecalho = formatter.formatCellValue(headerRow.getCell(j));
                    String valor = formatter.formatCellValue(currentRow.getCell(j));

                    if (!valor.trim().isEmpty()) {
                        // "Sutura" o cabeçalho ao valor: Ex: [Campus: Araguaína]
                        linhaNarrativa.append("[").append(cabecalho).append(": ").append(valor).append("] ");
                    }
                }
                sb.append(linhaNarrativa).append("\n");
            }
        }
        return sb.toString();
    }

    // 2. Limpeza
    public String limparTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isEmpty()) return "";

        return textoBruto
                // 1. Remove números de página isolados (ex: " 12 " ou "\n12\n")
                .replaceAll("(?m)^\\s*\\d+\\s*$", "")

                // 2. Une palavras que foram separadas por hífen no final da linha (ex: "compu-\ntador")
                .replaceAll("(\\w+)-\\s*\\n\\s*(\\w+)", "$1$2")

                // 3. Transforma quebras de linha no meio de frases em espaços
                .replaceAll("(?<=\\w)\\s*\\n\\s*(?=\\w)", " ")

                // 4. Remove múltiplos espaços, tabs e quebras de linha excessivas
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\n{2,}", "\n")

                .trim();
    }

    // 3. Fatiamento (Chunking)
    public List<String> fatiarTexto(String textoLimpo) {
        // Definimos nossos parâmetros da Sprint 2
        int tamanhoChunk = 512;
        int overlap = 64;

        // Dividimos o texto em palavras (aproximação de tokens para o TCC 1)
        String[] palavras = textoLimpo.split("\\s+");
        List<String> chunks = new ArrayList<>();

        // Se o texto for menor que o chunk, retorna ele inteiro
        if (palavras.length <= tamanhoChunk) {
            chunks.add(textoLimpo);
            return chunks;
        }

        // Lógica da janela deslizante
        for (int i = 0; i < palavras.length; i += (tamanhoChunk - overlap)) {
            StringBuilder chunkAtual = new StringBuilder();

            // Pega as 512 palavras a partir da posição atual
            for (int j = i; j < i + tamanhoChunk && j < palavras.length; j++) {
                chunkAtual.append(palavras[j]).append(" ");
            }

            chunks.add(chunkAtual.toString().trim());

            // Para evitar loop infinito se o texto acabar
            if (i + tamanhoChunk >= palavras.length) break;
        }

        return chunks;
    }

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

    public void mockSalvarNoBanco(List<String> chunks, String equipe, String acesso, String nomeArquivo) {
        List<ChunkDTO> listaParaSalvar = montarChunksComMetadados(chunks, equipe, acesso, nomeArquivo);

        // Código para salvar em ficheiro JSON
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

            // Cria a pasta output se não existir
            File pasta = new File("output");
            if (!pasta.exists()) pasta.mkdirs();

            File ficheiroJson = new File("output/processado_" + System.currentTimeMillis() + ".json");
            mapper.writeValue(ficheiroJson, listaParaSalvar);

            System.out.println("\n✅ SUCESSO: Dados exportados para: " + ficheiroJson.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Erro ao gerar ficheiro JSON: " + e.getMessage());
        }
    }
}
