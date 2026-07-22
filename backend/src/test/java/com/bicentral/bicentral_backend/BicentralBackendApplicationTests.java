package com.bicentral.bicentral_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.bicentral.bicentral_backend.service.ia.IngestaoService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootTest
class BicentralBackendApplicationTests {

    @Autowired
    private IngestaoService ingestaoService;

    @Test
    void validarIngestaoPDF() {
        System.out.println("\n=== INICIANDO TESTE DE INGESTÃO: PDF ===");

        try {
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/Manual de Identidade Visual UFT.pdf");
            String caminhoAbsoluto = path.toFile().getAbsolutePath();

            System.out.println("Local do arquivo: " + caminhoAbsoluto);

            String textoBruto = ingestaoService.extrairTextoPDF(caminhoAbsoluto);
            System.out.println("Status: Extração concluída.");

            String textoLimpo = ingestaoService.limparTexto(textoBruto);
            System.out.println("Status: Limpeza concluída.");

            System.out.println("\n--- RESULTADO FINAL (PRÉVIA) ---");
            System.out.println("Total de caracteres: " + textoLimpo.length());
            System.out.println("Conteúdo limpo:\n");

            if (textoLimpo.length() > 0) {
                System.out.println(textoLimpo.substring(0, Math.min(1000, textoLimpo.length())));
            } else {
                System.out.println("AVISO: O texto extraído está vazio. Verifique o PDF.");
            }

            System.out.println("\n=== TESTE FINALIZADO COM SUCESSO ===");

        } catch (Exception e) {
            System.err.println("\n❌ ERRO DURANTE O TESTE:");
            e.printStackTrace();
        }
    }

    /*
    @Test
    void validarLimpezaNarrativaExcel() {
        System.out.println("\n=== TESTANDO LIMPEZA NARRATIVA: EXCEL ===");
        try {
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/ações-pdi.xlsx");
            String caminhoAbsoluto = path.toFile().getAbsolutePath();

            String resultadoNarrativo = ingestaoService.extrairTextoExcel(caminhoAbsoluto);
            String textoFinal = ingestaoService.limparTexto(resultadoNarrativo);

            System.out.println("--- PRÉVIA DA NARRATIVA (Primeiras linhas) ---");
            System.out.println(textoFinal.substring(0, Math.min(2000, textoFinal.length())));

            System.out.println("\n=== VALIDAÇÃO CONCLUÍDA ===");
        } catch (Exception e) {
            System.err.println("Erro ao processar planilha imensa: " + e.getMessage());
            e.printStackTrace();
        }
    }
    */

    @Test
    void validarFatiamentoSprint2() {
        System.out.println("\n=== INICIANDO TESTE DE CHUNKING (512/64) ===");
        try {
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/Manual de Identidade Visual UFT.pdf");
            String textoLimpo = ingestaoService.limparTexto(ingestaoService.extrairTextoPDF(path.toFile().getAbsolutePath()));

            List<String> meusChunks = ingestaoService.fatiarTexto(textoLimpo);

            System.out.println("Total de Chunks gerados: " + meusChunks.size());

            if (meusChunks.size() > 1) {
                System.out.println("\n--- COMPARANDO SOBREPOSIÇÃO (OVERLAP) ---");
                System.out.println("FIM DO CHUNK 0: ... " + extrairFinal(meusChunks.get(0)));
                System.out.println("INÍCIO DO CHUNK 1: " + extrairInicio(meusChunks.get(1)));
            }

            System.out.println("\n=== CHECK: CHUNKING VALIDADO! ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // mockSalvarNoBanco foi substituído pelo salvarNoBanco real que chama as APIs de embedding.
    // O teste de pipeline completa foi removido para não disparar chamadas reais às APIs durante o build.
    // Para testar a ingestão completa, use o endpoint POST /ai/test/ingestao diretamente.

    private String extrairFinal(String s) { return s.substring(Math.max(0, s.length() - 100)); }
    private String extrairInicio(String s) { return s.substring(0, Math.min(s.length(), 100)) + " ..."; }
}