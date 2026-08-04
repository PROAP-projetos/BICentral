package com.bicentral.bicentral_backend.dto.notificacao;

import java.util.List;

public record NotificacaoDTO(
        String emoji,
        String departamento,
        String mensagem,
        double percentual,
        List<TarefaCriticaDTO> tarefas) {
}
