package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.painel.GraficoSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.SerieGraficoDTO;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProiapServiceTest {

    private final ProiapService proiapService = new ProiapService(
            mock(AgenteProiap.class), mock(AgenteConsultaSql.class), mock(EstadoSessao.class),
            mock(EmbeddingService.class), mock(UsoIaService.class), mock(ChatHistoricoService.class),
            mock(MemoriaUsuarioService.class));

    private boolean painelTemDados(PainelSpecDTO spec) throws Exception {
        Method m = ProiapService.class.getDeclaredMethod("painelTemDados", PainelSpecDTO.class);
        m.setAccessible(true);
        return (boolean) m.invoke(proiapService, spec);
    }

    private PainelSpecDTO painel(List<GraficoSpecDTO> graficos) {
        return new PainelSpecDTO("painel", "msg", "titulo", graficos);
    }

    @Test
    void painelTemDados_SemGraficos_RetornaFalso() throws Exception {
        assertFalse(painelTemDados(painel(List.of())));
    }

    @Test
    void painelTemDados_GraficoComSerieVazia_RetornaFalso() throws Exception {
        GraficoSpecDTO grafico = new GraficoSpecDTO("Execução média", "gauge", List.of("PAT"), List.of());
        assertFalse(painelTemDados(painel(List.of(grafico))));
    }

    @Test
    void painelTemDados_GraficoComSerieDeValoresVazia_RetornaFalso() throws Exception {
        SerieGraficoDTO serieSemValores = new SerieGraficoDTO("Execução", List.of());
        GraficoSpecDTO grafico = new GraficoSpecDTO("Execução média", "gauge", List.of("PAT"), List.of(serieSemValores));
        assertFalse(painelTemDados(painel(List.of(grafico))));
    }

    @Test
    void painelTemDados_ComValorReal_RetornaTrue() throws Exception {
        SerieGraficoDTO serieComValor = new SerieGraficoDTO("Execução", List.of(52.18));
        GraficoSpecDTO grafico = new GraficoSpecDTO("Execução média", "gauge", List.of("PAT"), List.of(serieComValor));
        assertTrue(painelTemDados(painel(List.of(grafico))));
    }
}
