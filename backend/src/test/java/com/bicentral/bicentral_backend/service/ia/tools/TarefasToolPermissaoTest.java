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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TarefasToolPermissaoTest {

    private static final long CONVIDADO = 7L;

    private final ConvidadoService convidadoService = mock(ConvidadoService.class);
    private TarefasTool tool;

    @BeforeEach
    void setUp() {
        UsuarioService usuarioService = mock(UsuarioService.class);
        Usuario usuario = new Usuario();
        usuario.setId(CONVIDADO);
        when(usuarioService.buscarPorEmail("convidado@fora.com")).thenReturn(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("convidado@fora.com", null));

        tool = new TarefasTool(mock(JdbcTemplate.class), usuarioService, mock(StatusExecucaoAgente.class), convidadoService);
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
}
