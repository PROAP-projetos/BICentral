package com.bicentral.bicentral_backend.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bicentral.bicentral_backend.service.ia.AgenteConsultaSql;
import com.bicentral.bicentral_backend.service.ia.AgenteProiap;
import com.bicentral.bicentral_backend.service.ia.AgenteRelatorio;
import com.bicentral.bicentral_backend.service.ia.ConsultaAcoesTool;
import com.bicentral.bicentral_backend.service.ia.RelatorioContextoTool;
import com.bicentral.bicentral_backend.service.ia.TarefasTool;

import dev.langchain4j.memory.chat.ChatMemoryProvider;

@Configuration
public class LangchainConfig {

    @Bean
    public AgenteProiap agenteProiap(ChatLanguageModel chatLanguageModel) {
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(2);
        return AiServices.builder(AgenteProiap.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    @Bean
    public AgenteConsultaSql agenteConsultaSql(ChatLanguageModel chatLanguageModel, ConsultaAcoesTool consultaAcoesTool, RelatorioContextoTool relatorioContextoTool, TarefasTool tarefasTool) {
        return AiServices.builder(AgenteConsultaSql.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(consultaAcoesTool, relatorioContextoTool, tarefasTool)
                .build();
    }

    @Bean
    public AgenteRelatorio agenteRelatorio(@Qualifier("geminiModel") ChatLanguageModel geminiModel) {
        return AiServices.builder(AgenteRelatorio.class)
                .chatLanguageModel(geminiModel)
                .build();
    }
}