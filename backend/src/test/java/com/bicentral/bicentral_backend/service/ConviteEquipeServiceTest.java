package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.equipe.AceiteConviteResponseDTO;
import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeResponseDTO;
import com.bicentral.bicentral_backend.model.ConviteEquipe;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.ConviteEquipeRepository;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import com.bicentral.bicentral_backend.service.auth.EmailService;
import com.bicentral.bicentral_backend.service.equipe.ConviteEquipeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConviteEquipeServiceTest {

    @Mock
    private ConviteEquipeRepository conviteEquipeRepository;

    @Mock
    private EquipeRepository equipeRepository;

    @Mock
    private MembroEquipeRepository membroEquipeRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ConviteEquipeService conviteEquipeService;

    private Usuario admin;
    private Usuario convidado;
    private Equipe equipe;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(conviteEquipeService, "frontendBaseUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(conviteEquipeService, "expiracaoHoras", 72L);

        admin = new Usuario();
        admin.setId(1L);
        admin.setEmail("admin@bicentral.com");
        admin.setNome("Admin");

        convidado = new Usuario();
        convidado.setId(2L);
        convidado.setEmail("convite@bicentral.com");
        convidado.setNome("Convidado");

        equipe = new Equipe();
        equipe.setId(7L);
        equipe.setNome("Equipe BI");
    }

    @Test
    void enviarConvite_Admin_Success() throws Exception {
        ConviteEquipeRequestDTO dto = new ConviteEquipeRequestDTO("convite@bicentral.com", Role.EDITOR);
        MembroEquipe membroAdmin = new MembroEquipe();
        membroAdmin.setUsuario(admin);
        membroAdmin.setEquipe(equipe);
        membroAdmin.setRole(Role.ADMIN);

        when(equipeRepository.findById(7L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(admin, equipe)).thenReturn(Optional.of(membroAdmin));
        when(usuarioRepository.findByEmail("convite@bicentral.com")).thenReturn(Optional.of(convidado));
        when(membroEquipeRepository.findByUsuarioAndEquipe(convidado, equipe)).thenReturn(Optional.empty());
        when(conviteEquipeRepository.findByEquipeIdAndEmailAndStatus(7L, "convite@bicentral.com", ConviteEquipe.Status.PENDENTE))
                .thenReturn(Optional.empty());
        when(conviteEquipeRepository.save(any(ConviteEquipe.class))).thenAnswer(invocation -> {
            ConviteEquipe convite = invocation.getArgument(0);
            convite.setId(11L);
            return convite;
        });

        ConviteEquipeResponseDTO resposta = conviteEquipeService.enviarConvite(7L, dto, admin, "http://localhost:8080");

        assertNotNull(resposta);
        assertEquals("convite@bicentral.com", resposta.email());
        assertEquals(Role.EDITOR, resposta.role());
        assertEquals(ConviteEquipe.Status.PENDENTE, resposta.status());

        ArgumentCaptor<ConviteEquipe> captor = ArgumentCaptor.forClass(ConviteEquipe.class);
        verify(conviteEquipeRepository).save(captor.capture());
        ConviteEquipe salvo = captor.getValue();
        assertEquals(equipe, salvo.getEquipe());
        assertEquals(admin, salvo.getCriadoPor());
        assertNotNull(salvo.getToken());
        verify(emailService).sendTeamInviteEmail(eq(equipe), eq("convite@bicentral.com"), eq(Role.EDITOR), any(), any());
    }

    @Test
    void enviarConvite_NotAdmin_ThrowsAccessDenied() throws Exception {
        ConviteEquipeRequestDTO dto = new ConviteEquipeRequestDTO("viewer@bicentral.com", Role.VIEWER);
        MembroEquipe membroEditor = new MembroEquipe();
        membroEditor.setUsuario(admin);
        membroEditor.setEquipe(equipe);
        membroEditor.setRole(Role.EDITOR);

        when(equipeRepository.findById(7L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(admin, equipe)).thenReturn(Optional.of(membroEditor));

        assertThrows(AccessDeniedException.class,
                () -> conviteEquipeService.enviarConvite(7L, dto, admin, "http://localhost:8080"));

        verify(conviteEquipeRepository, never()).save(any());
        verify(emailService, never()).sendTeamInviteEmail(any(), any(), any(), any(), any());
    }

    @Test
    void aceitarConvite_ValidoCriaMembership() {
        ConviteEquipe convite = convitePendente("token-valido", LocalDateTime.now().plusHours(24), Role.EDITOR);

        when(conviteEquipeRepository.findByToken("token-valido")).thenReturn(Optional.of(convite));
        when(usuarioRepository.findByEmail("convite@bicentral.com")).thenReturn(Optional.of(convidado));
        when(membroEquipeRepository.findByUsuarioAndEquipe(convidado, equipe)).thenReturn(Optional.empty());
        when(membroEquipeRepository.save(any(MembroEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conviteEquipeRepository.save(any(ConviteEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AceiteConviteResponseDTO resposta = conviteEquipeService.aceitarConvite("token-valido", convidado);

        assertEquals("Convite aceito com sucesso.", resposta.mensagem());
        assertEquals("Equipe BI", resposta.equipeNome());
        assertEquals(Role.EDITOR, resposta.role());
        assertEquals(ConviteEquipe.Status.ACEITO, convite.getStatus());
        assertNotNull(convite.getAceitoEm());
    }

    @Test
    void aceitarConvite_ValidoAtualizaMembership() {
        ConviteEquipe convite = convitePendente("token-update", LocalDateTime.now().plusHours(24), Role.ADMIN);
        MembroEquipe membroExistente = new MembroEquipe();
        membroExistente.setUsuario(convidado);
        membroExistente.setEquipe(equipe);
        membroExistente.setRole(Role.VIEWER);

        when(conviteEquipeRepository.findByToken("token-update")).thenReturn(Optional.of(convite));
        //when(usuarioRepository.findByEmail("convite@bicentral.com")).thenReturn(Optional.of(convidado));
        when(membroEquipeRepository.findByUsuarioAndEquipe(convidado, equipe)).thenReturn(Optional.of(membroExistente));
        when(membroEquipeRepository.save(any(MembroEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conviteEquipeRepository.save(any(ConviteEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        conviteEquipeService.aceitarConvite("token-update", convidado);

        assertEquals(Role.ADMIN, membroExistente.getRole());
        assertEquals(ConviteEquipe.Status.ACEITO, convite.getStatus());
    }

    @Test
    void aceitarConvite_Expirado_ThrowsGone() {
        ConviteEquipe convite = convitePendente("token-expirado", LocalDateTime.now().minusMinutes(1), Role.VIEWER);

        when(conviteEquipeRepository.findByToken("token-expirado")).thenReturn(Optional.of(convite));
        when(conviteEquipeRepository.save(any(ConviteEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conviteEquipeService.aceitarConvite("token-expirado", convidado));

        assertEquals(HttpStatus.GONE, ex.getStatusCode());
        assertEquals("Este convite expirou.", ex.getReason());
        assertEquals(ConviteEquipe.Status.EXPIRADO, convite.getStatus());
    }

    @Test
    void aceitarConvite_Invalido_ThrowsNotFound() {
        when(conviteEquipeRepository.findByToken("invalido")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conviteEquipeService.aceitarConvite("invalido", convidado));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Convite inválido.", ex.getReason());
    }

    @Test
    void aceitarConvite_JaUsado_ThrowsConflict() {
        ConviteEquipe convite = convitePendente("token-usado", LocalDateTime.now().plusHours(10), Role.VIEWER);
        convite.setStatus(ConviteEquipe.Status.ACEITO);
        convite.setAceitoEm(LocalDateTime.now().minusMinutes(5));

        when(conviteEquipeRepository.findByToken("token-usado")).thenReturn(Optional.of(convite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conviteEquipeService.aceitarConvite("token-usado", convidado));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Este convite já foi utilizado.", ex.getReason());
    }

    private ConviteEquipe convitePendente(String token, LocalDateTime expiraEm, Role role) {
        ConviteEquipe convite = new ConviteEquipe();
        convite.setId(55L);
        convite.setEquipe(equipe);
        convite.setCriadoPor(admin);
        convite.setEmail("convite@bicentral.com");
        convite.setRole(role);
        convite.setToken(token);
        convite.setStatus(ConviteEquipe.Status.PENDENTE);
        convite.setExpiraEm(expiraEm);
        convite.setAceitoEm(null);
        return convite;
    }
}
