package com.bicentral.bicentral_backend.service.ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoriaUsuarioServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MemoriaUsuarioService service;

    @BeforeEach
    void setUp() {
        service = new MemoriaUsuarioService(jdbcTemplate);
    }

    @Test
    void montarBlocoMemoria_SemMemorias_RetornaVazio() {
        when(jdbcTemplate.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        assertEquals("", service.montarBlocoMemoria(1L));
    }

    @Test
    void montarBlocoMemoria_ComMemorias_ListaComIdParaPermitirSubstituicao() {
        when(jdbcTemplate.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of("id", 7L, "conteudo", "Prefere respostas resumidas.")
        ));

        String bloco = service.montarBlocoMemoria(1L);

        assertTrue(bloco.contains("[id 7]"));
        assertTrue(bloco.contains("Prefere respostas resumidas."));
    }

    @Test
    void salvar_ComIdParaSubstituir_DesativaAAntigaAntesDeInserirANova() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1L), eq("formato_resposta"), eq("Prefere tabelas.")))
                .thenReturn(99L);

        Long novoId = service.salvar(1L, "formato_resposta", "Prefere tabelas.", 7L);

        assertEquals(99L, novoId);
        verify(jdbcTemplate).update(contains("SET ativo = false"), eq(7L), eq(1L));
    }

    @Test
    void salvar_SemIdParaSubstituir_ApenasInsereSemDesativarNada() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1L), eq("formato_resposta"), eq("Prefere tabelas.")))
                .thenReturn(5L);

        Long novoId = service.salvar(1L, "formato_resposta", "Prefere tabelas.", null);

        assertEquals(5L, novoId);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
