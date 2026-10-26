package com.bicentral.bicentral_backend.service.ia.lgpd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PseudonimizadorTest {

    private final Pseudonimizador p = new Pseudonimizador(List.of(
            "Maria Fernanda Costa",
            "Maria Souza Lima",
            "JOSÉ CARLOS DA ROCHA",
            "Andre Luiz Costa e Silva",
            "Clara Mendes Prado",
            "Leia Barros Nunes",
            "Ana Santana Ferreira dos Santos"));

    @Test
    void nomeCompletoViraCodigoEVolta() {
        String mascarado = p.mascarar("A tarefa é de Maria Fernanda Costa, 80% concluída.");

        assertFalse(mascarado.contains("Maria"));
        assertTrue(mascarado.matches(".*\\[PESSOA_[0-9a-f]+\\].*"));
        assertEquals("A tarefa é de Maria Fernanda Costa, 80% concluída.", p.desmascarar(mascarado));
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        String mascarado = p.mascarar("tarefas do jose carlos da rocha e do ANDRÉ LUIZ COSTA E SILVA");

        assertFalse(mascarado.toLowerCase().contains("jose"));
        assertFalse(mascarado.toLowerCase().contains("andr"));
        assertEquals("tarefas do JOSÉ CARLOS DA ROCHA e do Andre Luiz Costa e Silva", p.desmascarar(mascarado));
    }

    @Test
    void mesmoNomeGeraSempreOMesmoCodigo() {
        Pseudonimizador outro = new Pseudonimizador(List.of("Clara Mendes Prado", "Maria Fernanda Costa"));

        assertEquals(p.mascarar("Maria Fernanda Costa"), outro.mascarar("Maria Fernanda Costa"));
    }

    @Test
    void primeiroEUltimoNomeIdentificamAPessoa() {
        String mascarado = p.mascarar("e a Maria Costa?");

        assertTrue(mascarado.contains("[PESSOA_"));
        assertEquals("e a Maria Fernanda Costa?", p.desmascarar(mascarado));
    }

    @Test
    void primeiroNomeAmbiguoVoltaComoFoiEscrito() {
        String mascarado = p.mascarar("quais as tarefas da Maria?");

        assertTrue(mascarado.contains("[NOME_"));
        assertFalse(mascarado.contains("Maria"));
        assertEquals("quais as tarefas da Maria?", p.desmascarar(mascarado));
    }

    @Test
    void primeiroNomeQueEPalavraComumNaoETrocado() {
        String texto = "Como deixar mais clara a descrição?";

        assertEquals(texto, p.mascarar(texto));
        assertTrue(p.mascarar("Clara Mendes Prado").contains("[PESSOA_"));
    }

    @Test
    void naoTrocaPedacoDePalavra() {
        String texto = "Mariana revisou o relatório";

        assertEquals(texto, p.mascarar(texto));
    }

    @Test
    void palavraQueEPrimeiroNomeDeUmaPessoaSoNaoETrocadaNoComecoDaFrase() {
        String texto = "Percentual baixo não é atraso. Leia o título de cada ação e leia as metas.";

        assertEquals(texto, p.mascarar(texto));
        assertTrue(p.mascarar("tarefas da Leia Barros Nunes").contains("[PESSOA_"));
    }

    @Test
    void primeiroNomeSozinhoEmMinusculaOuNoComecoDaFraseNaoETrocado() {
        assertEquals("tarefas da maria", p.mascarar("tarefas da maria"));
        assertEquals("Maria tem tarefas?", p.mascarar("Maria tem tarefas?"));
        assertTrue(p.mascarar("e a Maria?").contains("[NOME_"));
    }

    @Test
    void primeiroNomeComNomeDoMeioIdentificaAPessoa() {
        String mascarado = p.mascarar("quais as tarefas da ana santana?");

        assertFalse(mascarado.toLowerCase().contains("santana"));
        assertEquals("quais as tarefas da Ana Santana Ferreira dos Santos?", p.desmascarar(mascarado));
    }

    @Test
    void apagaEmailECpf() {
        String mascarado = p.mascarar("contato: fulano.tal@uft.edu.br, CPF 123.456.789-09");

        assertEquals("contato: [EMAIL], CPF [CPF]", mascarado);
    }

    @Test
    void desmascaraCodigoSemColchetes() {
        String codigo = p.mascarar("Maria Fernanda Costa");
        String semColchetes = codigo.substring(1, codigo.length() - 1);

        assertEquals("Responsável: Maria Fernanda Costa", p.desmascarar("Responsável: " + semColchetes));
    }

    @Test
    void codigoDesconhecidoFicaComoEsta() {
        assertEquals("[PESSOA_abcdef]", p.desmascarar("[PESSOA_abcdef]"));
    }

    @Test
    void textoVazioOuNuloNaoQuebra() {
        assertEquals("", p.mascarar(""));
        assertEquals(null, p.mascarar(null));
        assertEquals(null, p.desmascarar(null));
        assertEquals("sem nomes aqui", new Pseudonimizador(List.of()).mascarar("sem nomes aqui"));
    }
}
