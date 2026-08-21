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

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

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
    public AgenteConsultaSql agenteConsultaSql(@Qualifier("groqModel") ChatLanguageModel chatLanguageModel, ConsultaAcoesTool consultaAcoesTool, RelatorioContextoTool relatorioContextoTool, TarefasTool tarefasTool) {
        // 8 em vez de 2: uma única chamada de ferramenta já gera 3-4 mensagens (pergunta, chamada,
        // resultado, resposta final); com janela menor a pergunta original é evictada no meio do
        // turno e o modelo perde o fio, repetindo chamadas de ferramenta (viu isso travar em loop
        // gerando o mesmo relatório várias vezes). 8 cobre 1-2 chamadas de ferramenta com folga.
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(8);
        return AiServices.builder(AgenteConsultaSql.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(consultaAcoesTool, relatorioContextoTool, tarefasTool)
                .build();
    }

    @Bean
    public AgenteRelatorio agenteRelatorio(@Qualifier("groqModel") ChatLanguageModel geminiModel) {
        return AiServices.builder(AgenteRelatorio.class)
                .chatLanguageModel(geminiModel)
                .build();
    }
}