package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.state.EstadoSessao;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProiapServiceSugestoesTest {

    private final ProiapService service = new ProiapService(
            mock(AgenteProiap.class), mock(AgenteConsultaSql.class), mock(EstadoSessao.class),
            mock(EmbeddingService.class), mock(UsoIaService.class), mock(ChatHistoricoService.class),
            mock(MemoriaUsuarioService.class));

    @SuppressWarnings("unchecked")
    private List<String> sugestoes(List<ToolExecution> execucoes, String respostaFinal) throws Exception {
        Method m = ProiapService.class.getDeclaredMethod("montarSugestoes", List.class, String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(service, execucoes, respostaFinal);
    }

    private ToolExecution tool(String nome, String resultado) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name(nome).arguments("{}").build())
                .result(resultado)
                .build();
    }

    @Test
    void usaApenasAUltimaFerramentaDoTurno() throws Exception {
        List<String> chips = sugestoes(List.of(
                tool("ranquearDepartamentosPorExecucaoPAT", "| Departamento | % |"),
                tool("buscarMinhasTarefas", "| Ação | Tarefa |")), "Aqui estão suas tarefas.");
        assertTrue(chips.contains("Quais dessas estão atrasadas?"));
        assertFalse(chips.stream().anyMatch(c -> c.contains("distribuição de status")));
    }

    @Test
    void semChipsQuandoOResultadoVeioVazio() throws Exception {
        assertTrue(sugestoes(List.of(tool("buscarTarefas", "Nenhuma tarefa encontrada com esses filtros.")), "Não achei.").isEmpty());
        assertTrue(sugestoes(List.of(tool("buscarTarefas", "Não encontrei esse departamento.")), "Não achei.").isEmpty());
    }

    @Test
    void semChipsQuandoARelatorioFalhou() throws Exception {
        assertTrue(sugestoes(List.of(
                tool("solicitarGeracaoRelatorio", "Não foi possível gerar esse relatório: marcador sem ação.")), "Não deu.").isEmpty());
    }

    @Test
    void semChipsQuandoARespostaTerminaEmPergunta() throws Exception {
        assertTrue(sugestoes(List.of(tool("buscarTarefas", "| Ação | Tarefa |")),
                "Não tenho esse dado exato. Você quer dizer o percentual de execução? ").isEmpty());
    }

    @Test
    void semFerramentaSemChips() throws Exception {
        assertTrue(sugestoes(List.of(), "Oi!").isEmpty());
        assertTrue(sugestoes(null, "Oi!").isEmpty());
    }

    @Test
    void respeitaOLimiteDeTresChips() throws Exception {
        List<String> chips = sugestoes(List.of(tool("solicitarGeracaoRelatorio", "Relatório solicitado.")), "Pedido enviado.");
        assertEquals(3, chips.size());
    }
}
