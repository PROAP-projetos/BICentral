package com.bicentral.bicentral_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modelos da família gpt-5.6 mandam parâmetros opcionais de tool-calling explicitamente
 * como null no JSON (em vez de simplesmente omitir a chave, como o Groq fazia). O
 * DefaultToolExecutor do langchain4j 1.0.0-beta1 não trata esse caso — ao converter um
 * argumento null pro tipo do parâmetro Java, ele chama métodos como argument.toString()
 * sem checar null antes, e lança NullPointerException. Esse wrapper remove do JSON as
 * chaves com valor null antes de delegar pro executor real, fazendo "parâmetro ausente"
 * (que o DefaultToolExecutor já trata certo) em vez de "parâmetro com valor null".
 */
public class NullSafeToolExecutor implements ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DefaultToolExecutor delegate;

    public NullSafeToolExecutor(Object object, Method method) {
        this.delegate = new DefaultToolExecutor(object, method);
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        ToolExecutionRequest requestSemNulos = ToolExecutionRequest.builder()
                .id(toolExecutionRequest.id())
                .name(toolExecutionRequest.name())
                .arguments(removerChavesNulas(toolExecutionRequest.arguments()))
                .build();
        return delegate.execute(requestSemNulos, memoryId);
    }

    private String removerChavesNulas(String argumentosJson) {
        if (argumentosJson == null || argumentosJson.isBlank()) {
            return argumentosJson;
        }
        try {
            Map<String, Object> mapa = MAPPER.readValue(argumentosJson, LinkedHashMap.class);
            mapa.values().removeIf(Objects::isNull);
            return MAPPER.writeValueAsString(mapa);
        } catch (Exception e) {
            // JSON em formato inesperado: segue com o original e deixa o
            // DefaultToolExecutor lidar com o erro do jeito normal dele.
            return argumentosJson;
        }
    }
}
