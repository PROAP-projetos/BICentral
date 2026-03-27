package com.bicentral.bicentral_backend.controller;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ai/test")
public class AiTestController {

    @Qualifier("geminiModel")
    @Autowired
    private ChatLanguageModel geminiModel;

    @Qualifier("ollamaModel")
    @Autowired
    private ChatLanguageModel ollamaModel;

    @GetMapping("/gemini")
    public String testGemini(@RequestParam(defaultValue = "Olá!") String msg) {
        return geminiModel.chat(msg);
    }

    @GetMapping("/ollama")
    public String testOllama(@RequestParam(defaultValue = "Olá!") String msg) {
        return ollamaModel.chat(msg);
    }
}