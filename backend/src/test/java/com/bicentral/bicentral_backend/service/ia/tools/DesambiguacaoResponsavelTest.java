package com.bicentral.bicentral_backend.service.ia.tools;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesambiguacaoResponsavelTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private void bancoCom(List<Map<String, Object>> pessoas) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(pessoas);
    }

    private static Map<String, Object> pessoa(String nome, String departamento, long tarefas) {
        return Map.of("responsavel", nome, "departamentos", departamento, "tarefas", tarefas);
    }

    @Test
    void variasPessoasPedemParaPerguntarQualE() {
        bancoCom(List.of(
                pessoa("Maria Fernanda Costa", "PROTIC", 40),
                pessoa("Maria Souza Lima", "Gabinete do Reitor", 28),
                pessoa("Ana Maria Prado", "PROGEDEP", 3)));

        DesambiguacaoResponsavel.Resultado r = DesambiguacaoResponsavel.resolver(jdbc, "Maria", null);

        assertNull(r.nomeExato());
        assertTrue(r.mensagem().startsWith("VÁRIAS PESSOAS: 3 responsáveis"));
        assertTrue(r.mensagem().contains("| Maria Souza Lima | Gabinete do Reitor | 28 |"));
    }

    @Test
    void umaPessoaSoUsaONomeExato() {
        bancoCom(List.of(pessoa("Maria Souza Lima", "Gabinete do Reitor", 28)));

        DesambiguacaoResponsavel.Resultado r = DesambiguacaoResponsavel.resolver(jdbc, "maria souza", null);

        assertEquals("Maria Souza Lima", r.nomeExato());
        assertNull(r.mensagem());
    }

    @Test
    void nomeCompletoQueEPedacoDeOutroFicaComOExato() {
        bancoCom(List.of(
                pessoa("Maria Souza", "PROAP", 5),
                pessoa("Ana Maria Souza", "PROTIC", 9)));

        DesambiguacaoResponsavel.Resultado r = DesambiguacaoResponsavel.resolver(jdbc, "maria souza", null);

        assertEquals("Maria Souza", r.nomeExato());
    }

    @Test
    void ninguemEncontradoNaoInventaPessoa() {
        bancoCom(List.of());

        DesambiguacaoResponsavel.Resultado r = DesambiguacaoResponsavel.resolver(jdbc, "Fulana", null);

        assertNull(r.nomeExato());
        assertNull(r.mensagem());
    }

    // O mock não executa SQL: confere o texto montado, que já quebrou como "ILIKE ?GROUP BY".
    @Test
    void sqlMontadoTemEspacoEntreOsTrechosComESemFiltroDeVisibilidade() {
        bancoCom(List.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        DesambiguacaoResponsavel.resolver(jdbc, "Maria", null);
        DesambiguacaoResponsavel.resolver(jdbc, "Maria", List.of("proap"));

        verify(jdbc, org.mockito.Mockito.times(2)).queryForList(sql.capture(), any(Object[].class));
        for (String texto : sql.getAllValues()) {
            String normalizado = texto.replaceAll("\\s+", " ");
            assertTrue(normalizado.contains("ILIKE ? "), normalizado);
            assertTrue(normalizado.contains(" GROUP BY 1 ORDER BY 3 DESC, 1"), normalizado);
        }
        assertTrue(sql.getAllValues().get(1).replaceAll("\\s+", " ").contains("ILIKE ? AND LOWER(departamento) IN (?)"));
    }
}
