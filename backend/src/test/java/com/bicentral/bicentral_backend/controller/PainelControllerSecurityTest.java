package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.config.SecurityConfig;
import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.security.EquipeAuthorizationService;
import com.bicentral.bicentral_backend.security.JwtAuthenticationFilter;
import com.bicentral.bicentral_backend.service.PainelService;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PainelController.class)
@Import(SecurityConfig.class)
class PainelControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PainelService painelService;

    @MockitoBean
    private PowerBIScraperService scraperService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean(name = "equipeSecurity")
    private EquipeAuthorizationService equipeAuthorizationService;

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
    @WithMockUser(username = "viewer@bicentral.com")
    void listarDevePermitirViewer() throws Exception {
        PainelDTO painel = new PainelDTO();
        painel.setId(10L);
        painel.setNome("Painel Financeiro");

        when(equipeAuthorizationService.hasRole(7L, "VIEWER", "EDITOR", "ADMIN")).thenReturn(true);
        when(painelService.listarPorEquipe(7L)).thenReturn(List.of(painel));

        mockMvc.perform(get("/api/equipes/7/paineis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].nome").value("Painel Financeiro"));

        verify(painelService).listarPorEquipe(7L);
    }

    @Test
    @WithMockUser(username = "viewer@bicentral.com")
    void criarDeveNegarViewer() throws Exception {
        when(equipeAuthorizationService.hasRole(7L, "EDITOR", "ADMIN")).thenReturn(false);

        mockMvc.perform(post("/api/equipes/7/paineis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NovoPainelRequest("Painel RH",
                                "https://app.powerbi.com/view?r=abc"))))
                .andExpect(status().isForbidden());

        verify(painelService, never()).criarPainel(eq(7L), any());
    }

    @Test
    @WithMockUser(username = "editor@bicentral.com")
    void criarDevePermitirEditor() throws Exception {
        PainelDTO painel = new PainelDTO();
        painel.setId(20L);
        painel.setNome("Painel RH");

        when(equipeAuthorizationService.hasRole(7L, "EDITOR", "ADMIN")).thenReturn(true);
        when(painelService.criarPainel(eq(7L), any())).thenReturn(painel);

        mockMvc.perform(post("/api/equipes/7/paineis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NovoPainelRequest("Painel RH",
                                "https://app.powerbi.com/view?r=abc"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20L))
                .andExpect(jsonPath("$.nome").value("Painel RH"));

        verify(painelService).criarPainel(eq(7L), any());
    }

    @Test
    @WithMockUser(username = "admin@bicentral.com")
    void excluirDevePermitirAdmin() throws Exception {
        when(equipeAuthorizationService.hasRole(7L, "EDITOR", "ADMIN")).thenReturn(true);

        mockMvc.perform(delete("/api/equipes/7/paineis/20"))
                .andExpect(status().isNoContent());

        verify(painelService).deletarPainel(20L, 7L);
    }

    private record NovoPainelRequest(String nome, String linkPowerBi) {
    }
}
