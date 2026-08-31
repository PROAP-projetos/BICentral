package com.bicentral.bicentral_backend.state;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;

import lombok.Getter;
import lombok.Setter;

/**
 * Esta classe atua como a memória estruturada de cada usuário logado.
 * O Spring cria uma instância separada dessa classe para cada sessão ativa.
 */
@Component
@SessionScope
@Getter
@Setter
public class EstadoSessao {

    // Valores padrão ao iniciar o chat
    private String tipoGrafico = "bar";
    private Integer ano = 2024;
    private String curso = "Todos";
    private String indicador = "Matrículas";

    // O modelo selecionado pelo usuário no Angular
    private String modelo;

    // Equipe selecionada
    private Long equipeId;

    private boolean aguardandoConfirmacaoGrafico = false;
    private PainelSpecDTO painelPendente = null;

    // Sinaliza que um relatório foi solicitado durante o processamento da pergunta atual,
    // pra o front saber que deve abrir o painel de relatórios sozinho.
    private boolean relatorioGerado = false;

    // =====================================================
    // SETTERS COM LOG DE DEPURAÇÃO
    // =====================================================

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

    // Depuração
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