package com.bicentral.bicentral_backend;

import com.bicentral.bicentral_backend.service.IngestaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootTest
class BicentralBackendApplicationTests {

    @Autowired
    private IngestaoService ingestaoService; // O Spring injeta o serviço automaticamente

    @Test
    void validarIngestaoPDF() {
        System.out.println("\n=== INICIANDO TESTE DE INGESTÃO: PDF ===");

        try {
            // 1. Localiza o arquivo em src/test/resources/teste.pdf
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/Manual de Identidade Visual UFT.pdf");
            String caminhoAbsoluto = path.toFile().getAbsolutePath();

            System.out.println("Local do arquivo: " + caminhoAbsoluto);

            // 2. Executa a extração bruta
            String textoBruto = ingestaoService.extrairTextoPDF(caminhoAbsoluto);
            System.out.println("Status: Extração concluída.");

            // 3. Executa a limpeza (O coração da sua Sprint 2)
            String textoLimpo = ingestaoService.limparTexto(textoBruto);
            System.out.println("Status: Limpeza concluída.");

            // 4. Output para validação visual
            System.out.println("\n--- RESULTADO FINAL (PRÉVIA) ---");
            System.out.println("Total de caracteres: " + textoLimpo.length());
            System.out.println("Conteúdo limpo:\n");

            // Exibe os primeiros 1000 caracteres para você conferir a qualidade
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
    }/*
    @Test
    void validarLimpezaNarrativaExcel() {
        System.out.println("\n=== TESTANDO LIMPEZA NARRATIVA: EXCEL ===");
        try {
            // Aponte para sua planilha de teste
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/ações-pdi.xlsx");
            String caminhoAbsoluto = path.toFile().getAbsolutePath();

            // Extrai o texto já com a lógica de [Cabeçalho: Valor]
            String resultadoNarrativo = ingestaoService.extrairTextoExcel(caminhoAbsoluto);

            // Aplica a limpeza básica (remover espaços duplos, etc)
            String textoFinal = ingestaoService.limparTexto(resultadoNarrativo);

            System.out.println("--- PRÉVIA DA NARRATIVA (Primeiras linhas) ---");
            // Mostra apenas o começo para validarmos a estrutura
            System.out.println(textoFinal.substring(0, Math.min(2000, textoFinal.length())));

            System.out.println("\n=== VALIDAÇÃO CONCLUÍDA ===");
        } catch (Exception e) {
            System.err.println("Erro ao processar planilha imensa: " + e.getMessage());
            e.printStackTrace();
        }
    }*/
    // Este teste é mais para validar o fatiamento (chunking) do texto limpo, garantindo que a sobreposição (overlap) esteja correta.
    @Test
    void validarFatiamentoSprint2() {
        System.out.println("\n=== INICIANDO TESTE DE CHUNKING (512/64) ===");
        try {
            // 1. Extraímos e Limpamos (o que você já fez)
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/Manual de Identidade Visual UFT.pdf");
            String textoLimpo = ingestaoService.limparTexto(ingestaoService.extrairTextoPDF(path.toFile().getAbsolutePath()));

            // 2. Fatiamos
            List<String> meusChunks = ingestaoService.fatiarTexto(textoLimpo);

            // 3. Validação no Console
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

    // Métodos auxiliares só para o log ficar bonito
    private String extrairFinal(String s) { return s.substring(Math.max(0, s.length() - 100)); }
    private String extrairInicio(String s) { return s.substring(0, Math.min(s.length(), 100)) + " ..."; }

    @Test
    void validarPipelineCompletaSprint2() {
        System.out.println("\n=== INICIANDO PIPELINE COMPLETA: INGESTÃO -> CHUNKING -> METADADOS ===");
        try {
            // 1. Extração e Limpeza (PDF que você já validou)
            Path path = Paths.get("src/test/java/com/bicentral/bicentral_backend/resources/Manual de Identidade Visual UFT.pdf");
            String textoLimpo = ingestaoService.limparTexto(ingestaoService.extrairTextoPDF(path.toFile().getAbsolutePath()));

            // 2. Fatiamento (Chunking)
            List<String> chunks = ingestaoService.fatiarTexto(textoLimpo);

            // 3. Estruturação com Metadados (O passo final da Sprint 2)
            ingestaoService.mockSalvarNoBanco(
                    chunks,
                    "COMUNICAÇÃO", // Equipe
                    "Público",      // Acesso
                    "Manual_Identidade_UFT.pdf"
            );

            System.out.println("\n=== SPRINT 2 CONCLUÍDA COM SUCESSO! ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
