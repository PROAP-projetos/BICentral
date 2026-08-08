package com.bicentral.bicentral_backend.controller.ia;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bicentral.bicentral_backend.dto.ia.ChunkDTO;
import com.bicentral.bicentral_backend.service.ia.ConsultaService;
import com.bicentral.bicentral_backend.service.ia.IngestaoService;

import java.util.List;

@RestController
@RequestMapping("/ai/test")
public class AiTestController {

    @Autowired
    private ChatLanguageModel groqModel; 
    
    @Qualifier("ollamaModel")
    @Autowired
    private ChatLanguageModel ollamaModel;

    @Autowired
    private IngestaoService ingestaoService;

    @Autowired
    private ConsultaService consultaService;

    @GetMapping("/groq")
    public String testGroq(@RequestParam(defaultValue = "Olá!") String msg) {
        return groqModel.chat(msg);
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
        String nomeFinalArquivo = (nomeArquivo == null || nomeArquivo.isBlank())
                ? new java.io.File(caminhoArquivo).getName()
                : nomeArquivo;

        List<ChunkDTO> chunks = ingestaoService.executarIngestao(caminhoArquivo, nomeFinalArquivo, equipe, acesso, equipeId);
        return ResponseEntity.ok(chunks);
    }

    @GetMapping("/consulta")
    public ResponseEntity<List<String>> consultar(
            @RequestParam String pergunta,
            @RequestParam Long equipeId
    ){
        return ResponseEntity.ok(consultaService.buscar(pergunta, equipeId));
    }
}