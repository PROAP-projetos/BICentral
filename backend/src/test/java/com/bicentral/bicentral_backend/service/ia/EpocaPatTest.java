package com.bicentral.bicentral_backend.service.ia;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EpocaPatTest {

    @Test
    void primeiroSemestreCobraComTomLeve() {
        String bloco = EpocaPat.bloco(LocalDate.of(2026, 3, 10));
        assertTrue(bloco.contains("10/03/2026"));
        assertTrue(bloco.contains("1º SEMESTRE"));
        assertTrue(bloco.contains("tom leve"));
    }

    @Test
    void meioDoAnoEsperaMetade() {
        String bloco = EpocaPat.bloco(LocalDate.of(2026, 8, 1));
        assertTrue(bloco.contains("MEIO DO ANO"));
        assertTrue(bloco.contains("pelo menos 50%"));
    }

    @Test
    void retaFinalEPreocupante() {
        for (int mes : new int[]{10, 11, 12}) {
            String bloco = EpocaPat.bloco(LocalDate.of(2026, mes, 5));
            assertTrue(bloco.contains("RETA FINAL"));
            assertTrue(bloco.contains("preocupante"));
        }
    }

    @Test
    void fronteirasDeMes() {
        assertTrue(EpocaPat.bloco(LocalDate.of(2026, 6, 30)).contains("1º SEMESTRE"));
        assertTrue(EpocaPat.bloco(LocalDate.of(2026, 7, 1)).contains("MEIO DO ANO"));
        assertTrue(EpocaPat.bloco(LocalDate.of(2026, 9, 30)).contains("MEIO DO ANO"));
        assertTrue(EpocaPat.bloco(LocalDate.of(2026, 10, 1)).contains("RETA FINAL"));
    }
}
