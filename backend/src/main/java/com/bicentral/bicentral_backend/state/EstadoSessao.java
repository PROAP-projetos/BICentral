package com.bicentral.bicentral_backend.state;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Future;

@Component
@SessionScope
@Getter
@Setter
public class EstadoSessao {

    private String tipoGrafico = "bar";
    private Integer ano = 2024;
    private String curso = "Todos";
    private String indicador = "Matrículas";

    private String modelo;
    private Long equipeId;

    private boolean relatorioGerado = false;
    private boolean memoriaAtualizada = false;

    // Rastreia a execução de pergunta em andamento nessa sessão, pro botão de "parar"
    // conseguir cancelar de verdade (ver ProiapController). "transient" porque isso é só
    // controle de concorrência do processo atual — nunca deve ir pra serialização de sessão.
    private transient volatile Future<?> execucaoAtual;

    public void registrarExecucao(Future<?> execucao) {
        this.execucaoAtual = execucao;
    }

    // Chamado pelo endpoint /cancelar: pede a interrupção da thread que está processando a
    // pergunta atual e espera ela realmente terminar de desligar antes de devolver — sem
    // esse espera, uma pergunta nova poderia começar enquanto a cancelada ainda está mexendo
    // nesse mesmo EstadoSessao, que não tem nenhuma trava própria.
    public void cancelarExecucaoAtual() {
        Future<?> execucao = this.execucaoAtual;
        if (execucao == null || execucao.isDone()) return;
        execucao.cancel(true);
        aguardarTerminoSeAtivo();
    }

    // Chamado no início de toda pergunta nova: se a pergunta anterior dessa sessão ainda não
    // terminou de desligar (por exemplo, acabou de ser cancelada), espera aqui antes de
    // começar uma execução nova — garante que nunca haja duas threads mexendo nesse mesmo
    // EstadoSessao ao mesmo tempo.
    public void aguardarTerminoSeAtivo() {
        Future<?> execucao = this.execucaoAtual;
        if (execucao == null) return;
        try {
            execucao.get();
        } catch (Exception ignorado) {
            // CancellationException, InterruptedException, ExecutionException — não importa
            // o motivo aqui, só precisamos garantir que ela realmente terminou.
        }
    }

    @Override
    public String toString() {
        return "EstadoSessao{" +
                "indicador='" + indicador + '\'' +
                ", ano=" + ano +
                ", curso='" + curso + '\'' +
                ", tipoGrafico='" + tipoGrafico + '\'' +
                ", modelo='" + modelo + '\'' +
                ", equipeId=" + equipeId +
                '}';
    }
}