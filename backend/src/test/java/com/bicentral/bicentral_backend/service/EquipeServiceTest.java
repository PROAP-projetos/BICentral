package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.MembroEquipeRequestDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EquipeServiceTest {

    @Mock
    private EquipeRepository equipeRepository;

    @Mock
    private MembroEquipeRepository membroEquipeRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private EquipeService equipeService;

    private Usuario adminUser;
    private Usuario commonUser;
    private Equipe equipe;
    private MembroEquipe membroAdmin;

    @BeforeEach
    void setUp() {
        adminUser = new Usuario();
        adminUser.setId(1L);
        adminUser.setEmail("admin@test.com");

        commonUser = new Usuario();
        commonUser.setId(2L);
        commonUser.setEmail("user@test.com");

        equipe = new Equipe();
        equipe.setId(1L);
        equipe.setNome("Equipe Teste");

        membroAdmin = new MembroEquipe();
        membroAdmin.setUsuario(adminUser);
        membroAdmin.setEquipe(equipe);
        membroAdmin.setRole(Role.ADMIN);
    }

    @Test
    void adicionarMembro_Admin_Success() {
        MembroEquipeRequestDTO dto = new MembroEquipeRequestDTO("new@test.com", Role.VIEWER);
        Usuario novoUsuario = new Usuario();
        novoUsuario.setEmail("new@test.com");

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(adminUser, equipe)).thenReturn(Optional.of(membroAdmin));
        when(usuarioRepository.findByEmail("new@test.com")).thenReturn(Optional.of(novoUsuario));
        when(membroEquipeRepository.findByUsuarioAndEquipe(novoUsuario, equipe)).thenReturn(Optional.empty());
        when(membroEquipeRepository.save(any(MembroEquipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MembroEquipe result = equipeService.adicionarMembro(1L, dto, adminUser);

        assertNotNull(result);
        assertEquals(novoUsuario, result.getUsuario());
        assertEquals(Role.VIEWER, result.getRole());
    }

    @Test
    void adicionarMembro_NotAdmin_ThrowsAccessDenied() {
        MembroEquipeRequestDTO dto = new MembroEquipeRequestDTO("new@test.com", Role.VIEWER);
        MembroEquipe membroEditor = new MembroEquipe();
        membroEditor.setRole(Role.EDITOR);

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(commonUser, equipe)).thenReturn(Optional.of(membroEditor));

        assertThrows(AccessDeniedException.class, () -> equipeService.adicionarMembro(1L, dto, commonUser));
    }

    @Test
    void removerMembro_LastAdmin_ThrowsException() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(adminUser, equipe)).thenReturn(Optional.of(membroAdmin));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(membroEquipeRepository.findByEquipe(equipe)).thenReturn(Collections.singletonList(membroAdmin));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> equipeService.removerMembro(1L, 1L, adminUser));
        assertTrue(exception.getReason().contains("último administrador"));
    }

    @Test
    void alterarPapelMembro_DemoteLastAdmin_ThrowsException() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(adminUser, equipe)).thenReturn(Optional.of(membroAdmin));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(membroEquipeRepository.findByEquipe(equipe)).thenReturn(Collections.singletonList(membroAdmin));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> equipeService.alterarPapelMembro(1L, 1L, Role.VIEWER, adminUser));
        assertTrue(exception.getReason().contains("único administrador"));
    }

    @Test
    void deletarEquipe_NotAdmin_ThrowsAccessDenied() {
        MembroEquipe membroViewer = new MembroEquipe();
        membroViewer.setRole(Role.VIEWER);

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(commonUser, equipe)).thenReturn(Optional.of(membroViewer));

        assertThrows(AccessDeniedException.class, () -> equipeService.deletarEquipe(1L, commonUser));
    }

    @Test
    void deletarEquipe_Admin_Success() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(membroEquipeRepository.findByUsuarioAndEquipe(adminUser, equipe)).thenReturn(Optional.of(membroAdmin));

        equipeService.deletarEquipe(1L, adminUser);

        verify(equipeRepository, times(1)).delete(equipe);
    }
}
