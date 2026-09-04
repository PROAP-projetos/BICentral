package com.bicentral.bicentral_backend.state;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;

import lombok.Getter;
import lombok.Setter;

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

    private boolean aguardandoConfirmacaoGrafico = false;
    private PainelSpecDTO painelPendente = null;
    private Long interacaoIdPendente = null;
    private boolean relatorioGerado = false;

    public void setAguardandoConfirmacaoGrafico(boolean valor) {
        System.out.println(
            "\n[DEBUG ESTADO] setAguardandoConfirmacaoGrafico(" + valor + ")"
        );

        Thread.dumpStack();

        this.aguardandoConfirmacaoGrafico = valor;
    }

    public void setPainelPendente(PainelSpecDTO painel) {
        System.out.println(
            "\n[DEBUG ESTADO] setPainelPendente(" +
            (painel != null ? "OBJETO" : "NULL") +
            ")"
        );

        Thread.dumpStack();

        this.painelPendente = painel;
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
                ", aguardandoConfirmacaoGrafico=" + aguardandoConfirmacaoGrafico +
                ", painelPendente=" + (painelPendente != null ? "OBJETO" : "NULL") +
                '}';
    }
}