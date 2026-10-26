package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultaAcoesToolSemTarefaTest {

    private static final String PROTIC = "Pró-Reitoria de Tecnologia da Informação e Comunicação - PROTIC";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConsultaAcoesTool tool = new ConsultaAcoesTool(jdbc, mock(StatusExecucaoAgente.class));

    private static Map<String, Object> departamento(String nome, String tipo, long total, long semTarefa) {
        Map<String, Object> linha = new HashMap<>();
        linha.put("departamento", nome);
        linha.put("tipo_unidade", tipo);
        linha.put("total", total);
        linha.put("sem_tarefa", semTarefa);
        return linha;
    }

    @Test
    void listaAsAcoesSemTarefaDoDepartamento() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv ->
                ((String) inv.getArgument(0)).contains("COUNT(*) AS total")
                        ? List.of(departamento(PROTIC, "UG", 74, 2))
                        : List.of(
                                Map.of("departamento", PROTIC, "codigo_acao", "U 4.1.7.8",
                                        "titulo_acao", "U 4.1.7.8 - Viabilizar a aquisição de tecnologias | " + PROTIC + " (UG)",
                                        "percentual", new BigDecimal("0.00")),
                                Map.of("departamento", PROTIC, "codigo_acao", "U 5.1.2.5",
                                        "titulo_acao", "U 5.1.2.5 - Modernizar a infraestrutura | " + PROTIC + " (UG)",
                                        "percentual", new BigDecimal("0.00"))));

        String resposta = tool.acoesSemTarefa("PROTIC", null);

        assertTrue(resposta.startsWith(PROTIC + ": 2 de 74 ações sem nenhuma tarefa"));
        assertTrue(resposta.contains("| U 4.1.7.8 - Viabilizar a aquisição de tecnologias | 0% |"));
        assertTrue(!resposta.contains("(UG)"), "título sem o nome do departamento colado");
    }

    @Test
    void departamentoSemFaltaDizQueTodasTemTarefa() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(departamento(PROTIC, "UG", 74, 0)));

        assertTrue(tool.acoesSemTarefa("PROTIC", null).startsWith("Todas as 74 ações de " + PROTIC));
    }

    @Test
    void geralFiltraPorTipoDeUnidade() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                departamento("Pró-Reitoria de Graduação - PROGRAD", "UG", 118, 10),
                departamento(PROTIC, "UG", 74, 8)));

        String resposta = tool.acoesSemTarefa(null, "ug");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("AND tu.tipo_unidade = ?"));
        assertArrayEquals(new Object[]{"UG"}, params.getValue());
        assertTrue(resposta.startsWith("PAT, só UG: 18 de 192 atribuições"));
        assertTrue(resposta.contains("| " + PROTIC + " | 74 | 8 | 10"), "sem coluna Tipo quando filtrado");
    }
}
