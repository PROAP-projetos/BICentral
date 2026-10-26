package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.diretrizes.AlinhamentoLoteIADTO;
import com.bicentral.bicentral_backend.dto.diretrizes.AlinhamentoTarefaIADTO;
import com.bicentral.bicentral_backend.dto.diretrizes.SituacaoAlinhamento;
import com.bicentral.bicentral_backend.service.ia.AlinhamentoDiretrizesService.Analise;
import com.bicentral.bicentral_backend.service.ia.AlinhamentoDiretrizesService.TarefaPendente;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlinhamentoDiretrizesServiceTest {

    // Ação 1.1.1.1 da base: INEP 1.1–1.5 e 5.8; IESGo 1111, 1112, 2113, 2114, 3133.
    private static final String ACAO = "U 1.1.1.1";

    private static DiretrizesService base;

    @BeforeAll
    static void carregarBase() throws Exception {
        base = new DiretrizesService();
    }

    private static TarefaPendente tarefa(long id, String acao) {
        return new TarefaPendente(id, acao, "Tarefa " + id, "descrição", "hash" + id);
    }

    @Test
    void loteFechaNoLimiteDeTarefas() {
        List<TarefaPendente> tarefas = new ArrayList<>();
        for (long i = 1; i <= 65; i++) {
            tarefas.add(tarefa(i, ACAO));
        }
        List<List<TarefaPendente>> lotes = AlinhamentoDiretrizesService.montarLotes(tarefas);
        assertEquals(List.of(30, 30, 5), lotes.stream().map(List::size).toList());
    }

    @Test
    void loteFechaNoLimiteDeAcoesSemSepararTarefasDaMesmaAcao() {
        List<TarefaPendente> tarefas = new ArrayList<>();
        for (int a = 1; a <= 10; a++) {
            tarefas.add(tarefa(a * 10, "U 1." + a));
            tarefas.add(tarefa(a * 10 + 1, "U 1." + a));
        }
        List<List<TarefaPendente>> lotes = AlinhamentoDiretrizesService.montarLotes(tarefas);
        assertEquals(List.of(16, 4), lotes.stream().map(List::size).toList());
        assertEquals("U 1.9", lotes.get(1).get(0).codigoAcao());
    }

    @Test
    void descartaCodigoQueNaoEDaAcao() {
        AlinhamentoLoteIADTO resposta = new AlinhamentoLoteIADTO(List.of(
                new AlinhamentoTarefaIADTO(1L, SituacaoAlinhamento.ALINHADA,
                        List.of("INEP 1.2", "INEP 4.7", "IESGo 2113 (A)"), "cobre autoavaliação")));

        List<Analise> analises = AlinhamentoDiretrizesService.validar(List.of(tarefa(1, ACAO)), resposta, base);

        assertEquals(1, analises.size());
        assertEquals("ALINHADA", analises.get(0).situacao());
        assertEquals(List.of("INEP 1.2", "IESGo 2113"), analises.get(0).diretrizes());
    }

    @Test
    void alinhadaSemCodigoValidoViraSemEvidencia() {
        AlinhamentoLoteIADTO resposta = new AlinhamentoLoteIADTO(List.of(
                new AlinhamentoTarefaIADTO(1L, SituacaoAlinhamento.ALINHADA, List.of("INEP 9.9"), "inventado")));

        Analise analise = AlinhamentoDiretrizesService.validar(List.of(tarefa(1, ACAO)), resposta, base).get(0);

        assertEquals("SEM_EVIDENCIA", analise.situacao());
        assertTrue(analise.diretrizes().isEmpty());
    }

    @Test
    void ignoraIdForaDoLoteERepetido() {
        AlinhamentoLoteIADTO resposta = new AlinhamentoLoteIADTO(List.of(
                new AlinhamentoTarefaIADTO(99L, SituacaoAlinhamento.ALINHADA, List.of("INEP 1.2"), "outra tarefa"),
                new AlinhamentoTarefaIADTO(1L, SituacaoAlinhamento.PARCIAL, List.of("INEP 1.2"), "primeira"),
                new AlinhamentoTarefaIADTO(1L, SituacaoAlinhamento.ALINHADA, List.of("INEP 1.2"), "repetida")));

        List<Analise> analises = AlinhamentoDiretrizesService.validar(List.of(tarefa(1, ACAO), tarefa(2, ACAO)), resposta, base);

        assertEquals(1, analises.size());
        assertEquals("PARCIAL", analises.get(0).situacao());
    }

    @Test
    void normalizaGrafiasDosCodigos() {
        assertEquals(List.of("INEP 1.2", "IESGo 2132", "INEP 4.10"),
                AlinhamentoDiretrizesService.normalizarCodigos(List.of("inep 1.2", "IESGo 2132 (A, D)", "INEP4.10")));
    }
}
