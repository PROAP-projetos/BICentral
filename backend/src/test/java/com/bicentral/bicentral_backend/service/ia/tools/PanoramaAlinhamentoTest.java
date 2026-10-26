package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.service.ia.DiretrizesService;
import com.bicentral.bicentral_backend.service.ia.tools.PanoramaAlinhamento.Linha;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanoramaAlinhamentoTest {

    private static final String ACAO = "U 1.1.1.1";
    private static final OffsetDateTime DATA = OffsetDateTime.parse("2026-10-26T15:00:00Z");

    private static DiretrizesService base;

    @BeforeAll
    static void carregarBase() throws Exception {
        base = new DiretrizesService();
    }

    private static Linha linha(long id, String departamento, String situacao, String diretrizes) {
        return new Linha(id, departamento, ACAO, "Tarefa " + id, situacao, diretrizes, "motivo " + id,
                situacao == null ? null : DATA);
    }

    @Test
    void semNenhumaAnaliseOrientaOAgente() {
        String texto = PanoramaAlinhamento.montar("PAT inteiro", List.of(linha(1, "PROAP", null, null)), base);
        assertTrue(texto.startsWith("PANORAMA AINDA NÃO GERADO"));
        assertTrue(texto.contains("compararTarefasComDiretrizes"));
    }

    @Test
    void contaSituacoesEListaDiretrizSemCobertura() {
        String texto = PanoramaAlinhamento.montar("PAT inteiro", List.of(
                linha(1, "PROAP", "ALINHADA", "INEP 1.2; IESGo 2113"),
                linha(2, "PROAP", "PARCIAL", "INEP 1.1"),
                linha(3, "PROAP", "SEM_EVIDENCIA", ""),
                linha(4, "PROAP", "SEM_EVIDENCIA", "")), base);

        assertTrue(texto.contains("- Alinhadas: 1 (25%)"));
        assertTrue(texto.contains("- Parciais: 1 (25%)"));
        assertTrue(texto.contains("- Sem evidência: 2 (50%)"));
        assertTrue(texto.contains("INEP 1.2 "), "diretriz alinhada aparece nas mais cobertas");
        assertTrue(texto.contains("- INEP 5.8 "), "diretriz da ação sem tarefa alinhada ou parcial aparece como sem cobertura");
        assertFalse(texto.contains("- INEP 1.1 Evolução institucional —"), "parcial conta como coberta");
        assertTrue(texto.contains("análise mais recente em 26/10/2026"));
        assertFalse(texto.contains("COBERTURA PARCIAL"));
    }

    @Test
    void avisaCoberturaParcialESeparaSemDiretriz() {
        String texto = PanoramaAlinhamento.montar("PAT inteiro", List.of(
                linha(1, "PROAP", "ALINHADA", "INEP 1.2"),
                linha(2, "PROAP", "SEM_DIRETRIZ", ""),
                linha(3, "PROAP", null, null)), base);

        assertTrue(texto.contains("COBERTURA PARCIAL: 1 tarefa(s)"));
        assertTrue(texto.contains("- Alinhadas: 1 (100%)"));
        assertTrue(texto.contains("Fora da conta: 1 tarefa(s)"));
    }

    @Test
    void comparaDepartamentosDoMenorParaOMaiorAlinhamento() {
        List<Linha> linhas = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            linhas.add(linha(i, "PROGRAD", "ALINHADA", "INEP 1.2"));
            linhas.add(linha(100 + i, "PROEST", i == 0 ? "ALINHADA" : "SEM_EVIDENCIA", i == 0 ? "INEP 1.2" : ""));
        }
        String texto = PanoramaAlinhamento.montar("PAT inteiro", linhas, base);

        assertTrue(texto.indexOf("| PROEST | 5 | 20%") < texto.indexOf("| PROGRAD | 5 | 100%"));
    }
}
