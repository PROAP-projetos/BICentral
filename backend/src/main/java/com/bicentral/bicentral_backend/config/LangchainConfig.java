package com.bicentral.bicentral_backend.config;

import com.bicentral.bicentral_backend.service.AgenteProiap;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.memory.chat.ChatMemoryProvider;

@Configuration
public class LangchainConfig {

    @Bean
    public AgenteProiap agenteProiap(ChatLanguageModel chatLanguageModel) {

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(5);
        return AiServices.builder(AgenteProiap.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
} 