package com.bicentral.bicentral_backend.controller.ia;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.PainelAtrasosDTO;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import com.bicentral.bicentral_backend.service.ia.ProiapService;
import com.bicentral.bicentral_backend.service.notificacao.NotificacaoService;

import java.util.List;

@RestController
@RequestMapping("/api/proiap")
@CrossOrigin(origins = "*")
public class ProiapController {

    private final ProiapService proiapService;
    private final EstadoSessao estadoSessao;
    private final NotificacaoService notificacaoService;

    public ProiapController(ProiapService proiapService, EstadoSessao estadoSessao, NotificacaoService notificacaoService) {
        this.proiapService = proiapService;
        this.estadoSessao = estadoSessao;
        this.notificacaoService = notificacaoService;
    }

    public record RequisicaoProiap(String texto, Long equipeId, String modelo, String sessaoId) {
    }

    @PostMapping("/perguntar")
    public Object fazerPergunta(@RequestBody RequisicaoProiap requisicao) {

        if (requisicao.modelo() != null) {
            estadoSessao.setModelo(requisicao.modelo());
        }

        if (requisicao.equipeId() != null) {
            estadoSessao.setEquipeId(requisicao.equipeId());
        }

        return proiapService.processarPergunta(requisicao.texto(), requisicao.sessaoId());
    }

    @GetMapping("/testar-notificacoes/{usuarioId}")
    public List<NotificacaoDTO> testarNotificacoes(@PathVariable Long usuarioId) {
        return notificacaoService.gerarNotificacoes(usuarioId);
    }

    @GetMapping("/painel-atrasos")
    public PainelAtrasosDTO painelAtrasos(@RequestParam String departamento) {
        return notificacaoService.gerarPainelAtrasos(departamento);
    }
}