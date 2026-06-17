package com.bicentral.bicentral_backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.bicentral.bicentral_backend.service.ProiapService;
import com.bicentral.bicentral_backend.state.EstadoSessao;

@RestController
@RequestMapping("/api/proiap")
@CrossOrigin(origins = "*")
public class ProiapController {
    
    private final ProiapService proiapService;
    private final EstadoSessao estadoSessao; 

    public ProiapController(ProiapService proiapService, EstadoSessao estadoSessao){
        this.proiapService = proiapService;
        this.estadoSessao = estadoSessao;
    }

    public record RequisicaoProiap(String texto, Long equipeId, String modelo) {}

    @PostMapping("/perguntar")
    public Object fazerPergunta(@RequestBody RequisicaoProiap requisicao) {
        
        // 1. Atualiza a memória da sessão com os dados que vieram da tela
        if (requisicao.modelo() != null) {
            estadoSessao.setModelo(requisicao.modelo());
        }
        
        if (requisicao.equipeId() != null) {
            estadoSessao.setEquipeId(requisicao.equipeId());
        }

        // 2. Manda apenas a string da pergunta para o agente processar
        return proiapService.processarPergunta(requisicao.texto());
    }
}