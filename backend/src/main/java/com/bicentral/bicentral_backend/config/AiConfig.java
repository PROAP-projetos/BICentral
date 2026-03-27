package com.bicentral.bicentral_backend.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${langchain4j.google-ai-gemini.api-key}")
    private String geminiApiKey;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name:llama3}")
    private String ollamaModelName;

    @Bean("geminiModel")
    public ChatLanguageModel geminiModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .build();
    }

    @Bean("ollamaModel")
    public ChatLanguageModel ollamaModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .build();
    }
}