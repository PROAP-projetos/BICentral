package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultaAcoesToolRankingTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConsultaAcoesTool tool = new ConsultaAcoesTool(jdbc, mock(StatusExecucaoAgente.class));

    private ArgumentCaptor<String> sql() {
        return ArgumentCaptor.forClass(String.class);
    }

    @Test
    void filtraPorTipoENomeJuntos() {
        tool.ranquearDepartamentosPorExecucaoPAT("melhores", "UA", 500, "Palmas");

        ArgumentCaptor<String> sql = sql();
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("WHERE tipo_unidade = ? AND departamento ILIKE ?"));
        assertArrayEquals(new Object[]{"UA", "%Palmas%", 500}, params.getValue());
    }

    @Test
    void semFiltroNaoMontaWhere() {
        tool.ranquearDepartamentosPorExecucaoPAT("piores", null, null, null);

        ArgumentCaptor<String> sql = sql();
        verify(jdbc).queryForList(sql.capture(), org.mockito.ArgumentMatchers.<Object[]>any());
        assertFalse(sql.getValue().contains("WHERE"));
    }

    @Test
    void resumoExecutivoUsaAsMesmasContasDoPainel() {
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("total", 536L, "concluidas", 24L, "paradas", 100L));
        when(jdbc.queryForList(anyString())).thenReturn(
                List.of(
                        Map.of("departamento", "Unidade A - AAA", "media", 90.0),
                        Map.of("departamento", "Unidade B - BBB", "media", 50.0),
                        Map.of("departamento", "Unidade C - CCC", "media", 0.0),
                        Map.of("departamento", "Unidade D - DDD", "media", 0.0)),
                List.of());

        String resumo = tool.resumoExecutivoPAT();

        assertTrue(resumo.contains("536 ações únicas"));
        assertTrue(resumo.contains("24 concluídas"));
        assertTrue(resumo.contains("412 em andamento"));
        assertTrue(resumo.contains("100 paradas"));
        assertTrue(resumo.contains("3 abaixo de 60%"));
        assertTrue(resumo.contains("1ª: Unidade A - AAA — 90%"));
        assertTrue(resumo.contains("2 UGs empatadas em 0% (posições 3 a 4 de 4): Unidade D - DDD; Unidade C - CCC"));
        assertTrue(resumo.contains("- 2ª: Unidade B - BBB — 50%"));
        assertTrue(resumo.contains("COMPARTILHADAS PARADAS EM TODAS AS UNIDADES: nenhuma"));
    }

    @Test
    void compararAvisaQuandoAUnidadeNaoExiste() {
        String resposta = tool.compararUnidadesPAT("Unidade Inexistente", "Outra");

        assertTrue(resposta.startsWith("Nenhuma unidade encontrada no PAT com o nome 'Unidade Inexistente'"));
    }

    @Test
    void acoesSemExecucaoListaUgsComProporcao() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("departamento", "Unidade A - AAA", "total", 8L, "zeradas", 4L, "zeradas_compartilhadas", 1L, "ugs_com_zeradas", 2L, "zeradas_total", 5L),
                Map.of("departamento", "Unidade B - BBB", "total", 3L, "zeradas", 1L, "zeradas_compartilhadas", 0L, "ugs_com_zeradas", 2L, "zeradas_total", 5L)));

        String resposta = tool.acoesSemExecucaoPorUG(null);

        assertTrue(resposta.contains("2 UGs têm pelo menos uma ação sem execução, somando 5 atribuições zeradas"));
        assertTrue(resposta.contains("| Unidade A - AAA | 8 | 4 | 50% | 1 |"));
        assertTrue(resposta.contains("| Unidade B - BBB | 3 | 1 | 33,30% | 0 |"));
    }
}
