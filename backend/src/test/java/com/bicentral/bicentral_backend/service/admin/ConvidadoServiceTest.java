package com.bicentral.bicentral_backend.service.admin;

import com.bicentral.bicentral_backend.service.auth.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConvidadoServiceTest {

    private static final long USUARIO = 7L;

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EmailService emailService;
    @Mock private AdminService adminService;

    private ConvidadoService service;

    @BeforeEach
    void setUp() {
        service = new ConvidadoService(jdbcTemplate, emailService, adminService);
    }

    private void convidado(boolean ehConvidado) {
        when(jdbcTemplate.queryForObject(contains("usuarios_convidados"), eq(Integer.class), eq(USUARIO)))
                .thenReturn(ehConvidado ? 1 : 0);
    }

    private void gerencia(String departamento, boolean gerencia) {
        when(jdbcTemplate.queryForObject(contains("gerentes_departamento"), eq(Integer.class), eq(USUARIO), eq(departamento)))
                .thenReturn(gerencia ? 1 : 0);
    }

    @Test
    void quemNaoEhConvidadoVeTodasAsPessoas() {
        convidado(false);
        assertTrue(service.podeVerPessoas(USUARIO, "PROEX"));
    }

    @Test
    void adminConvidadoVeTodasAsPessoas() {
        convidado(true);
        when(adminService.isAdmin(USUARIO)).thenReturn(true);
        assertTrue(service.podeVerPessoas(USUARIO, "PROEX"));
    }

    @Test
    void convidadoVeApenasPessoasDoDepartamentoQueGerencia() {
        convidado(true);
        gerencia("PROAD", true);
        gerencia("PROEX", false);
        assertTrue(service.podeVerPessoas(USUARIO, "PROAD"));
        assertFalse(service.podeVerPessoas(USUARIO, "PROEX"));
    }

    @Test
    void convidadoSemDepartamentoNaoVePessoaNenhuma() {
        convidado(true);
        assertFalse(service.podeVerPessoas(USUARIO, null));
        assertFalse(service.podeVerPessoas(USUARIO, "  "));
        assertFalse(service.podeVerPessoas(null, "PROAD"));
    }

    @Test
    void conviteDeQuemNaoTemContaVaiPorTokenSemExporOEmailNoLink() {
        when(jdbcTemplate.queryForMap(anyString(), eq("fulana@empresa.com")))
                .thenThrow(new EmptyResultDataAccessException(1));

        boolean confirmado = service.adicionar("fulana@empresa.com");

        assertFalse(confirmado);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendConvidadoEmailAsync(eq("fulana@empresa.com"), isNull(), link.capture());
        assertTrue(link.getValue().contains("/cadastro?convite="));
        assertFalse(link.getValue().contains("fulana"));
        assertTrue(link.getValue().length() > 60);
    }

    @Test
    void tokenDeConviteResolveParaOEmailConvidado() {
        when(jdbcTemplate.queryForList(contains("convidados_pendentes"), eq(String.class), eq("tok123")))
                .thenReturn(List.of("fulana@empresa.com"));
        when(jdbcTemplate.queryForList(contains("convidados_pendentes"), eq(String.class), eq("outro")))
                .thenReturn(List.of());

        assertTrue(service.emailDoConvite("tok123").isPresent());
        assertFalse(service.emailDoConvite("outro").isPresent());
        assertFalse(service.emailDoConvite(null).isPresent());
        assertFalse(service.emailDoConvite("  ").isPresent());
    }

    @Test
    void soViraConvidadoQuemUsaUmTokenValido() {
        when(jdbcTemplate.update(contains("DELETE FROM convidados_pendentes WHERE token"), eq("valido"))).thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE FROM convidados_pendentes WHERE token"), eq("invalido"))).thenReturn(0);

        service.consumirConvite("invalido", USUARIO);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO usuarios_convidados"), anyLong());

        service.consumirConvite("valido", USUARIO);
        verify(jdbcTemplate).update(contains("INSERT INTO usuarios_convidados"), eq(USUARIO));
    }
}
