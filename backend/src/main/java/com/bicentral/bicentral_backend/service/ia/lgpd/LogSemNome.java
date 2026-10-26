package com.bicentral.bicentral_backend.service.ia.lgpd;

// Em produção o console vira log da hospedagem, fora do sistema: registra só se o nome veio preenchido.
public final class LogSemNome {

    private LogSemNome() {
    }

    public static String campo(String valor) {
        return valor == null || valor.isBlank() ? "null" : "(informado)";
    }
}
