package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import com.bicentral.bicentral_backend.service.IngestaoService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.bicentral.bicentral_backend.service.ConsultaService;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/ai/test")
public class AiTestController {

    @Qualifier("geminiModel")
    @Autowired
    private ChatLanguageModel geminiModel;

    @Qualifier("ollamaModel")
    @Autowired
    private ChatLanguageModel ollamaModel;

    @Autowired
    private IngestaoService ingestaoService;

    @GetMapping("/gemini")
    public String testGemini(@RequestParam(defaultValue = "Olá!") String msg) {
        return geminiModel.chat(msg);
    }

    @GetMapping("/ollama")
    public String testOllama(@RequestParam(defaultValue = "Olá!") String msg) {
        return ollamaModel.chat(msg);
    }

    @PostMapping("/ingestao")
    public ResponseEntity<List<ChunkDTO>> ingerir(
            @RequestParam String caminhoArquivo,
            @RequestParam(defaultValue = "COMUNICACAO") String equipe,
            @RequestParam(defaultValue = "Publico") String acesso,
            @RequestParam(required = false) String nomeArquivo,
            @RequestParam Long equipeId
    ) throws Exception {
        String textoBruto;
        String caminhoLower = caminhoArquivo.toLowerCase();

        if (caminhoLower.endsWith(".xlsx")) {
            textoBruto = ingestaoService.extrairTextoExcel(caminhoArquivo);
        } else {
            textoBruto = ingestaoService.extrairTextoPDF(caminhoArquivo);
        }

        String textoLimpo = ingestaoService.limparTexto(textoBruto);
        List<String> chunks = ingestaoService.fatiarTexto(textoLimpo);

        String nomeFinalArquivo = (nomeArquivo == null || nomeArquivo.isBlank())
                ? new java.io.File(caminhoArquivo).getName()
                : nomeArquivo;

        // gera embeddings e salva no Supabase
        ingestaoService.salvarNoBanco(chunks, equipe, acesso, nomeFinalArquivo, equipeId);

        // retorna os chunks para visualização
        List<ChunkDTO> json = ingestaoService.montarChunksComMetadados(chunks, equipe, acesso, nomeFinalArquivo);
        return ResponseEntity.ok(json);
    }

    @Autowired
    private ConsultaService consultaService;

    @GetMapping("/consulta")
    public ResponseEntity<List<String>> consultar(
            @RequestParam String pergunta,
            @RequestParam Long equipeId
    ){
        return ResponseEntity.ok(consultaService.buscar(pergunta, equipeId));
    }
}