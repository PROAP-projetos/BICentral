package com.bicentral.bicentral_backend.service.ia.lgpd;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// Mascara toda mensagem que sai, inclusive respostas antigas da IA (a memória guarda desmascarado).
// Na volta desmascara também os argumentos de tool, para a tool consultar o banco com o nome real.
public class ModeloPseudonimizado implements ChatLanguageModel {

    private final ChatLanguageModel delegado;
    private final Supplier<Pseudonimizador> pseudonimizador;
    private final boolean logarEnvio;

    public ModeloPseudonimizado(ChatLanguageModel delegado, Supplier<Pseudonimizador> pseudonimizador) {
        this(delegado, pseudonimizador, false);
    }

    public ModeloPseudonimizado(ChatLanguageModel delegado, Supplier<Pseudonimizador> pseudonimizador, boolean logarEnvio) {
        this.delegado = delegado;
        this.pseudonimizador = pseudonimizador;
        this.logarEnvio = logarEnvio;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        // Uma instância só por chamada: se o dicionário recarregar no meio, ida e volta usam o mesmo.
        Pseudonimizador p = pseudonimizador.get();

        ChatRequest mascarado = ChatRequest.builder()
                .messages(chatRequest.messages().stream().map(m -> transformar(m, p::mascarar)).toList())
                .parameters(chatRequest.parameters())
                .build();

        if (logarEnvio) {
            mascarado.messages().forEach(m -> System.out.println(">>> LGPD ENVIO: " + m));
        }

        ChatResponse resposta = delegado.chat(mascarado);

        if (logarEnvio) {
            System.out.println(">>> LGPD RESPOSTA (antes de desmascarar): " + resposta.aiMessage());
        }

        return ChatResponse.builder()
                .aiMessage((AiMessage) transformar(resposta.aiMessage(), p::desmascarar))
                .metadata(resposta.metadata())
                .build();
    }

    // O AiServices usa chat(ChatRequest); a interface exige estes também.
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatResponse resposta = chat(ChatRequest.builder().messages(messages).build());
        return Response.from(resposta.aiMessage(), resposta.tokenUsage(), resposta.finishReason());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatResponse resposta = chat(ChatRequest.builder().messages(messages)
                .parameters(ChatRequestParameters.builder().toolSpecifications(toolSpecifications).build())
                .build());
        return Response.from(resposta.aiMessage(), resposta.tokenUsage(), resposta.finishReason());
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegado.defaultRequestParameters();
    }

    // Sem repassar, o AiServices acha que o modelo não suporta JSON schema e o painel (structured output) quebra.
    @Override
    public Set<Capability> supportedCapabilities() {
        return delegado.supportedCapabilities();
    }

    static ChatMessage transformar(ChatMessage mensagem, UnaryOperator<String> f) {
        if (mensagem instanceof SystemMessage s) {
            return SystemMessage.from(f.apply(s.text()));
        }
        if (mensagem instanceof UserMessage u) {
            List<Content> conteudos = u.contents().stream()
                    .map(c -> c instanceof TextContent t ? (Content) TextContent.from(f.apply(t.text())) : c)
                    .toList();
            return u.name() != null ? UserMessage.from(u.name(), conteudos) : UserMessage.from(conteudos);
        }
        if (mensagem instanceof ToolExecutionResultMessage r) {
            return ToolExecutionResultMessage.from(r.id(), r.toolName(), f.apply(r.text()));
        }
        if (mensagem instanceof AiMessage a) {
            String texto = a.text() != null ? f.apply(a.text()) : null;
            if (!a.hasToolExecutionRequests()) {
                return texto != null ? AiMessage.from(texto) : a;
            }
            List<ToolExecutionRequest> pedidos = a.toolExecutionRequests().stream()
                    .map(t -> ToolExecutionRequest.builder()
                            .id(t.id())
                            .name(t.name())
                            .arguments(f.apply(t.arguments()))
                            .build())
                    .toList();
            return texto != null ? AiMessage.from(texto, pedidos) : AiMessage.from(pedidos);
        }
        return mensagem;
    }
}
