package com.bicentral.bicentral_backend.service.ia.lgpd;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeloPseudonimizadoTest {

    private static final List<String> NOMES = List.of("Maria Fernanda Costa", "Maria Souza Lima", "José Carlos da Rocha");

    /** Modelo falso: guarda o texto de tudo o que recebeu e responde o que o teste mandar. */
    private static class ModeloFalso implements ChatLanguageModel {
        final List<String> recebido = new ArrayList<>();
        final Function<String, AiMessage> resposta;

        ModeloFalso(Function<String, AiMessage> resposta) {
            this.resposta = resposta;
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            StringBuilder tudo = new StringBuilder();
            for (ChatMessage m : chatRequest.messages()) {
                tudo.append(m.toString()).append('\n');
            }
            recebido.add(tudo.toString());
            return ChatResponse.builder().aiMessage(resposta.apply(tudo.toString())).build();
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        }
    }

    private static String codigoDe(String nome) {
        return new Pseudonimizador(NOMES).mascarar(nome);
    }

    @Test
    void nenhumNomeSaiParaOProvedor() {
        ModeloFalso falso = new ModeloFalso(t -> AiMessage.from("ok"));
        ModeloPseudonimizado modelo = new ModeloPseudonimizado(falso, () -> new Pseudonimizador(NOMES));

        modelo.chat(ChatRequest.builder().messages(
                SystemMessage.from("Usuário logado responde como José Carlos da Rocha."),
                UserMessage.from("quais as tarefas da Maria Fernanda Costa?"),
                AiMessage.from(List.of(ToolExecutionRequest.builder().id("1").name("buscarTarefas")
                        .arguments("{\"responsavel\":\"Maria Fernanda Costa\"}").build())),
                ToolExecutionResultMessage.from("1", "buscarTarefas", "Maria Fernanda Costa: 5 tarefas; contato maria@uft.edu.br"),
                AiMessage.from("A Maria Fernanda Costa tem 5 tarefas.")
        ).build());

        String enviado = falso.recebido.get(0);
        for (String parte : List.of("Maria", "José", "Jose", "Rocha", "Costa", "maria@uft.edu.br")) {
            assertFalse(enviado.contains(parte), "vazou: " + parte);
        }
        assertTrue(enviado.contains(codigoDe("Maria Fernanda Costa")));
    }

    @Test
    void respostaVoltaComONomeReal() {
        String codigo = codigoDe("Maria Fernanda Costa");
        ModeloFalso falso = new ModeloFalso(t -> AiMessage.from("A " + codigo + " tem 5 tarefas."));
        ModeloPseudonimizado modelo = new ModeloPseudonimizado(falso, () -> new Pseudonimizador(NOMES));

        ChatResponse resposta = modelo.chat(ChatRequest.builder()
                .messages(UserMessage.from("tarefas da Maria Fernanda Costa")).build());

        assertEquals("A Maria Fernanda Costa tem 5 tarefas.", resposta.aiMessage().text());
    }

    @Test
    void toolRecebeONomeRealNoArgumento() {
        ModeloFalso falso = new ModeloFalso(t -> AiMessage.from(List.of(ToolExecutionRequest.builder()
                .id("1").name("buscarTarefas")
                .arguments("{\"responsavel\":\"" + codigoDe("da Maria").substring(3) + "\"}").build())));
        ModeloPseudonimizado modelo = new ModeloPseudonimizado(falso, () -> new Pseudonimizador(NOMES));

        ChatResponse resposta = modelo.chat(ChatRequest.builder()
                .messages(UserMessage.from("tarefas da Maria")).build());

        assertTrue(falso.recebido.get(0).contains("[NOME_"));
        assertEquals("{\"responsavel\":\"Maria\"}",
                resposta.aiMessage().toolExecutionRequests().get(0).arguments());
    }

    @Test
    void repassaAsCapacidadesDoModelo() {
        ModeloPseudonimizado modelo = new ModeloPseudonimizado(new ModeloFalso(t -> AiMessage.from("ok")),
                () -> new Pseudonimizador(NOMES));

        assertTrue(modelo.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }
}
