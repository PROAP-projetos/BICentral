package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.config.SecurityConfig;
import com.bicentral.bicentral_backend.dto.ConviteEquipeResponseDTO;
import com.bicentral.bicentral_backend.model.ConviteEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.security.JwtAuthenticationFilter;
import com.bicentral.bicentral_backend.service.ConviteEquipeService;
import com.bicentral.bicentral_backend.service.EquipeService;
import com.bicentral.bicentral_backend.service.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EquipeController.class)
@Import(SecurityConfig.class)
class EquipeControllerInviteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EquipeService equipeService;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private ConviteEquipeService conviteEquipeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin@bicentral.com")
    void enviarConvite_DeveRetornar403ParaNaoAdmin() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("admin@bicentral.com");

        when(usuarioService.buscarPorEmail("admin@bicentral.com")).thenReturn(usuario);
        when(conviteEquipeService.enviarConvite(eq(7L), any(), eq(usuario), any()))
                .thenThrow(new AccessDeniedException("Apenas administradores podem enviar convites"));

        mockMvc.perform(post("/api/equipes/7/convites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConviteRequest("convite@bicentral.com", "VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Apenas administradores podem enviar convites"));
    }

    @Test
    @WithMockUser(username = "admin@bicentral.com")
    void enviarConvite_DeveCriarConviteParaAdmin() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("admin@bicentral.com");

        ConviteEquipeResponseDTO resposta = new ConviteEquipeResponseDTO(
                9L,
                7L,
                "Equipe BI",
                "convite@bicentral.com",
                Role.EDITOR,
                ConviteEquipe.Status.PENDENTE,
                LocalDateTime.now().plusDays(3)
        );

        when(usuarioService.buscarPorEmail("admin@bicentral.com")).thenReturn(usuario);
        when(conviteEquipeService.enviarConvite(eq(7L), any(), eq(usuario), any())).thenReturn(resposta);

        mockMvc.perform(post("/api/equipes/7/convites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConviteRequest("convite@bicentral.com", "EDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("convite@bicentral.com"))
                .andExpect(jsonPath("$.role").value("EDITOR"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    private record ConviteRequest(String email, String role) {
    }
}
