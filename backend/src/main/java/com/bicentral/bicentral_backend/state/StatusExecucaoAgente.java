package com.bicentral.bicentral_backend.state;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

// Estado bem pequeno, de propósito, separado do EstadoSessao (que já guarda estado maior e
// mais sensível a concorrência): só guarda uma frase amigável dizendo o que a pergunta em
// andamento está fazendo agora, pra dar feedback de verdade no "Pensando" do chat em vez de
// só um texto estático parado. O frontend faz polling em cima disso enquanto carregando=true
// (ver ProiapController.statusExecucao) — não é streaming de tokens, é streaming "de etapa".
@Component
@SessionScope
public class StatusExecucaoAgente {

    private static final String ETAPA_PADRAO = "Pensando";

    private volatile String etapaAtual = ETAPA_PADRAO;

    public String getEtapaAtual() {
        return etapaAtual;
    }

    public void definir(String etapa) {
        this.etapaAtual = etapa;
    }

    public void reiniciar() {
        this.etapaAtual = ETAPA_PADRAO;
    }
}
