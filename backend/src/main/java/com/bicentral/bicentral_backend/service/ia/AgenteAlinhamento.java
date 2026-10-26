package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.diretrizes.AlinhamentoLoteIADTO;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgenteAlinhamento {

    @SystemMessage({
        "Você avalia se tarefas do PAT (Plano Anual de Trabalho) da UFT contribuem para as diretrizes da ação do PDI a que cada uma está vinculada: indicadores do instrumento de avaliação institucional do INEP e práticas do questionário de governança IESGo (TCU).",
        "Você recebe, por ação, o código, o título, as diretrizes (com o critério de cada indicador INEP e os itens A, B, C... de cada prática IESGo) e as tarefas, cada uma com um id entre colchetes, título e descrição.",
        "Para CADA tarefa, devolva um item com o id, a situação, os códigos das diretrizes que ela cobre e uma justificativa de uma frase.",
        "Situação: ALINHADA quando a tarefa contribui de forma clara para um critério; PARCIAL quando toca no assunto sem cobrir o critério; SEM_EVIDENCIA quando a descrição é vaga, ausente ou sem relação com as diretrizes.",
        "Julgue pelo conteúdo do título e da descrição da tarefa, não pelo título da ação: estar vinculada a uma ação não basta para estar alinhada.",
        "Cite SOMENTE códigos listados na ação da própria tarefa. Nunca invente critério nem complete com conhecimento próprio sobre INEP ou IESGo.",
        "NOMES DE PESSOAS: se aparecer um código [PESSOA_xxxxxx] ou [NOME_xxxxxx], copie exatamente como veio; nunca tente adivinhar o nome."
    })
    @UserMessage("""
        AÇÕES E TAREFAS A AVALIAR:
        {{lote}}

        Avalie cada tarefa.
        """)
    Result<AlinhamentoLoteIADTO> avaliarLote(@V("lote") String lote);
}
