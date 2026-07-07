package com.bicentral.bicentral_backend.config;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Value("${langchain4j.google-ai-gemini.api-key}")
    private String geminiApiKey;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${cerebras.api.key:}")
    private String cerebrasApiKey;

    @Value("${sambanova.api.key:}")
    private String sambanovaApiKey;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name:llama3}")
    private String ollamaModelName;

    @Value("${ollama.embedding.model.name:nomic-embed-text}")
    private String ollamaEmbeddingModelName;

    // --- CHAT MODELS (LLMs) ---

    @Bean("groqModel")
    public ChatLanguageModel groqModel() {
        return OpenAiChatModel.builder()
                .apiKey(groqApiKey)
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("llama-3.3-70b-versatile")
                .temperature(0.0)
                .build();
    }

    @Bean("cerebrasModel")
    public ChatLanguageModel cerebrasModel() {
        return OpenAiChatModel.builder()
                .apiKey(cerebrasApiKey)
                .baseUrl("https://api.cerebras.ai/v1")
                .modelName("zai-glm-4.7") // openai-gpt-oss-120b, gemma-4-31b-it
                .temperature(0.0)
                .build();
    }

    @Bean("sambanovaModel")
    @Primary
    public ChatLanguageModel sambanovaModel() {
        return OpenAiChatModel.builder()
                .apiKey(sambanovaApiKey)
                .baseUrl("https://api.sambanova.ai/v1")
                .modelName("DeepSeek-V3.1") // Meta-Llama-3.3-70B-Instruct, gpt-oss-120b
                .build();
    }

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
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    // --- EMBEDDING MODELS ---

    @Bean("localEmbeddingModel")
    @Primary
    public EmbeddingModel localEmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean("geminiEmbeddingModel")
    public EmbeddingModel geminiEmbeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-embedding-001")
                .outputDimensionality(768)
                .build();
    }

    @Bean("ollamaEmbeddingModel")
    public EmbeddingModel ollamaEmbeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModelName)
                .build();
    }
}