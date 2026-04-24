package com.bicentral.bicentral_backend.security;

import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipeAuthorizationServiceTest {

    @Mock
    private MembroEquipeRepository membroEquipeRepository;

    @InjectMocks
    private EquipeAuthorizationService equipeAuthorizationService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("viewer@bicentral.com", null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devePermitirQuandoRoleEstaNaLista() {
        MembroEquipe membro = new MembroEquipe();
        membro.setRole(Role.VIEWER);

        when(membroEquipeRepository.findByUsuarioEmailAndEquipeId("viewer@bicentral.com", 7L))
                .thenReturn(Optional.of(membro));

        boolean permitido = equipeAuthorizationService.hasRole(7L, "VIEWER", "EDITOR", "ADMIN");

        assertTrue(permitido);
    }

    @Test
    void deveNegarQuandoRoleNaoEstaNaLista() {
        MembroEquipe membro = new MembroEquipe();
        membro.setRole(Role.VIEWER);

        when(membroEquipeRepository.findByUsuarioEmailAndEquipeId("viewer@bicentral.com", 7L))
                .thenReturn(Optional.of(membro));

        boolean permitido = equipeAuthorizationService.hasRole(7L, "EDITOR", "ADMIN");

        assertFalse(permitido);
    }

    @Test
    void deveNegarQuandoUsuarioNaoEhMembroDaEquipe() {
        when(membroEquipeRepository.findByUsuarioEmailAndEquipeId("viewer@bicentral.com", 7L))
                .thenReturn(Optional.empty());

        boolean permitido = equipeAuthorizationService.hasRole(7L, "VIEWER", "EDITOR", "ADMIN");

        assertFalse(permitido);
    }
}
