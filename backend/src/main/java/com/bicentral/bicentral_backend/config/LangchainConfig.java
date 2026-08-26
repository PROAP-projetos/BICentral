package com.bicentral.bicentral_backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bicentral.bicentral_backend.service.ia.AgenteConsultaSql;
import com.bicentral.bicentral_backend.service.ia.AgenteProiap;
import com.bicentral.bicentral_backend.service.ia.AgenteRelatorio;
import com.bicentral.bicentral_backend.service.ia.tools.ConsultaAcoesTool;
import com.bicentral.bicentral_backend.service.ia.tools.RelatorioContextoTool;
import com.bicentral.bicentral_backend.service.ia.tools.TarefasTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class LangchainConfig {

    @Bean
    public AgenteProiap agenteProiap(@Qualifier("openaiLunaModel") ChatLanguageModel chatLanguageModel) {
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(2);
        return AiServices.builder(AgenteProiap.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    @Bean
    public AgenteConsultaSql agenteConsultaSql(@Qualifier("openaiTerraModel") ChatLanguageModel chatLanguageModel, ConsultaAcoesTool consultaAcoesTool, RelatorioContextoTool relatorioContextoTool, TarefasTool tarefasTool) {
        // 24 em vez de 8: um turno com 2+ chamadas de ferramenta (ex: comparar dois departamentos)
        // já gera mensagem de usuário + assistente(tool_calls) + resultado da tool, x2, + resposta
        // final = 6+ mensagens SÓ desse turno. Com janela pequena, a MessageWindowChatMemory evicta
        // por contagem simples de mensagem e pode cortar a mensagem do assistente que pediu a tool
        // call mantendo a resposta da tool órfã — a OpenAI rejeita isso (400: "messages with role
        // 'tool' must be a response to a preceding message with 'tool_calls'"). 24 dá folga pra
        // vários turnos com múltiplas chamadas de ferramenta sem cortar um par no meio.
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(24);
        return AiServices.builder(AgenteConsultaSql.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(montarFerramentas(consultaAcoesTool, relatorioContextoTool, tarefasTool))
                .build();
    }

    @Bean
    public AgenteRelatorio agenteRelatorio(@Qualifier("openaiLunaModel") ChatLanguageModel geminiModel) {
        return AiServices.builder(AgenteRelatorio.class)
                .chatLanguageModel(geminiModel)
                .build();
    }

    /**
     * Monta o mesmo mapa ToolSpecification->ToolExecutor que .tools(Object...) monta
     * internamente, mas usando NullSafeToolExecutor em vez do DefaultToolExecutor padrão
     * (ver NullSafeToolExecutor para o motivo: gpt-5.6 manda null explícito em parâmetro
     * opcional não usado, e o DefaultToolExecutor do langchain4j 1.0.0-beta1 quebra nisso).
     */
    private static Map<ToolSpecification, ToolExecutor> montarFerramentas(Object... objetosComFerramentas) {
        Map<ToolSpecification, ToolExecutor> ferramentas = new LinkedHashMap<>();
        for (Object objeto : objetosComFerramentas) {
            for (Method method : objeto.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification especificacao = ToolSpecifications.toolSpecificationFrom(method);
                    ferramentas.put(especificacao, new NullSafeToolExecutor(objeto, method));
                }
            }
        }
        return ferramentas;
    }
}