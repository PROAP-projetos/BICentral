package com.bicentral.bicentral_backend.service.ia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiretrizesServiceTest {

    private final DiretrizesService service;

    DiretrizesServiceTest() throws Exception {
        service = new DiretrizesService();
    }

    @Test
    void carregaBaseInteira() {
        assertEquals(541, service.totalAcoes());
        assertEquals("V24", service.versao());
    }

    @Test
    void encontraAcaoComOuSemPrefixoDoPat() {
        var comPrefixo = service.buscar("U 1.1.1.1").orElseThrow();
        var semPrefixo = service.buscar("1.1.1.1").orElseThrow();

        assertEquals(comPrefixo, semPrefixo);
        assertEquals("1.1", comPrefixo.inepDetalhes().get(0).codigo());
        assertEquals("1111", comPrefixo.iesgoDetalhes().get(0).codigo());
        assertFalse(comPrefixo.iesgoDetalhes().get(0).itens().isEmpty());
    }

    @Test
    void codigoInexistenteOuNuloNaoQuebra() {
        assertTrue(service.buscar("U 9.9.9.9").isEmpty());
        assertTrue(service.buscar(null).isEmpty());
    }
}
