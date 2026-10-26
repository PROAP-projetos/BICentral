package com.bicentral.bicentral_backend.service.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Fora do pacote job: o SincronizacaoUftApplication varre aquele pacote e não tem os beans de IA.
// Desligado por padrão; liga com PROIAP_ALINHAMENTO_HABILITADO=true.
@Component
@ConditionalOnProperty(prefix = "proiap.alinhamento", name = "habilitado", havingValue = "true")
public class AlinhamentoDiretrizesJob {

    private final AlinhamentoDiretrizesService alinhamentoService;

    @Value("${proiap.alinhamento.lotes-por-execucao:5}")
    private int lotesPorExecucao;

    public AlinhamentoDiretrizesJob(AlinhamentoDiretrizesService alinhamentoService) {
        this.alinhamentoService = alinhamentoService;
        System.out.println(">>> ALINHAMENTO: job LIGADO (proiap.alinhamento.habilitado=true).");
    }

    // O servidor do Render dorme sem uso, então horário fixo pode nunca disparar: avança alguns lotes
    // a cada intervalo enquanto ele está acordado. Com tudo analisado, cada execução é uma consulta só.
    @Scheduled(fixedDelayString = "${proiap.alinhamento.intervalo:PT30M}", initialDelayString = "PT2M")
    public void executar() {
        AlinhamentoDiretrizesService.ResumoExecucao resumo = alinhamentoService.processarPendentes(lotesPorExecucao);
        if (resumo.tarefas() > 0) {
            System.out.println(">>> ALINHAMENTO: " + resumo.tarefas() + " tarefa(s) gravadas em " + resumo.lotes()
                    + " lote(s); tokens entrada=" + resumo.tokensEntrada() + ", saída=" + resumo.tokensSaida());
        }
    }
}
