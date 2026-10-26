package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.admin.ConvidadoService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TarefasToolPermissaoTest {

    private static final long CONVIDADO = 7L;

    private final ConvidadoService convidadoService = mock(ConvidadoService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private TarefasTool tool;

    @BeforeEach
    void setUp() {
        UsuarioService usuarioService = mock(UsuarioService.class);
        Usuario usuario = new Usuario();
        usuario.setId(CONVIDADO);
        when(usuarioService.buscarPorEmail("convidado@fora.com")).thenReturn(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("convidado@fora.com", null));

        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("total", 0L));
        tool = new TarefasTool(jdbc, usuarioService, mock(StatusExecucaoAgente.class), convidadoService);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private String buscar(String departamento) {
        return tool.buscarTarefas(departamento, null, null, null, null, null, null, null);
    }

    @Test
    void convidadoSemDepartamentoNaoVeTarefaNenhuma() {
        when(convidadoService.departamentosComPessoasVisiveis(CONVIDADO)).thenReturn(List.of());

        String resposta = buscar("PROAP");

        assertTrue(resposta.startsWith("SEM PERMISSÃO:"));
        assertTrue(resposta.contains("não tem permissão de acesso às tarefas"));
    }

    @Test
    void convidadoNaoVeTarefasDeOutroSetor() {
        when(convidadoService.departamentosComPessoasVisiveis(CONVIDADO))
                .thenReturn(List.of("pró-reitoria de extensão - proex"));

        assertTrue(buscar("PROAP").startsWith("SEM PERMISSÃO:"));
    }

    @Test
    void convidadoConsultaTarefasDoSetorQueGerencia() {
        when(convidadoService.departamentosComPessoasVisiveis(CONVIDADO))
                .thenReturn(List.of("pró-reitoria de avaliação e planejamento - proap"));

        assertFalse(buscar("PROAP").startsWith("SEM PERMISSÃO:"));
    }

    @Test
    void quemNaoEhConvidadoNaoTemRestricao() {
        when(convidadoService.departamentosComPessoasVisiveis(CONVIDADO)).thenReturn(null);

        assertFalse(buscar("PROAP").startsWith("SEM PERMISSÃO:"));
    }

    @Test
    void resumoDeStatusCobreTodasAsTarefasNaoSoAAmostra() {
        when(convidadoService.departamentosComPessoasVisiveis(CONVIDADO)).thenReturn(null);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "total", 69L, "concluidas", 40L, "em_andamento", 25L, "atrasadas", 4L, "zeradas", 12L, "media", new java.math.BigDecimal("72.50")));
        // Desambiguação do responsável: uma pessoa só com esse nome; a listagem de tarefas vem vazia.
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv ->
                ((String) inv.getArgument(0)).contains("string_agg(DISTINCT departamento")
                        ? java.util.List.of(Map.of("responsavel", "Idelma Teste", "departamentos", "PROAP", "tarefas", 69L))
                        : java.util.List.of());

        String resposta = tool.buscarTarefas(null, null, null, "Idelma", null, null, null, null);

        assertTrue(resposta.contains("RESUMO DE TODAS AS 69 TAREFAS"));
        assertTrue(resposta.contains("40 concluídas (100%), 25 em andamento no prazo, 4 atrasadas; 12 ainda em 0%"));
        assertTrue(resposta.contains("média de conclusão 72,50%") || resposta.contains("média de conclusão 72.50%"));
    }
}
