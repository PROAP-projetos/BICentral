package com.bicentral.bicentral_backend.dto.ia;

import java.util.List;

public record RespostaTextualDTO(
    String texto,
    List<String> fontes,
<<<<<<< HEAD
    boolean relatorioGerado
) {}
=======
    boolean relatorioGerado,
    List<String> sugestoes,
    Long interacaoId,
    boolean memoriaAtualizada
) {}
>>>>>>> d7f41a2f7ee04ff86c43972e609226b3fc0f6bb5
