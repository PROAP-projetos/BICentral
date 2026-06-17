package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.AnaliseComando;
import com.bicentral.bicentral_backend.dto.GraficoSpec;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgenteProiap {

    // ==========================================
    // SKILL 0: ANALISADOR DE COMANDOS (ROTEAMENTO)
    // ==========================================
    @SystemMessage({
        "Você é um Analista de Dados da PROAP extraindo parâmetros da frase do usuário.",
        "Extraia a intenção, ano, curso, indicador e tipo de gráfico.",
        "REGRA CRÍTICA DE ROTEAMENTO:",
        "1. Classifique a intenção como 'GRAFICO' EXCLUSIVAMENTE se o usuário pedir explicitamente uma visualização usando palavras como 'gráfico', 'desenhe', 'mostre visualmente', 'tabela', 'pizza', 'barras'.",
        "2. Se o usuário apenas fizer uma pergunta sobre os dados (ex: 'como está o andamento', 'qual foi o maior', 'resumo de', 'quais os dados'), classifique ESTRITAMENTE como 'RESPOSTA', mesmo que envolva números.",
        "REGRA 3: Se um filtro (como ano ou curso) não foi explicitamente mencionado na frase, retorne null. NUNCA deduza ou invente valores padrão.",
        "REGRA 4: Se o usuário pedir para alterar o 'título', 'formato' ou 'cores' de um gráfico, NÃO altere o campo 'indicador'. O indicador deve conter apenas o nome da métrica raiz (ex: Matrículas, Taxa de Evasão) para não quebrar a busca no banco de dados.",
        "Retorne apenas o JSON estruturado."
    })
    AnaliseComando analisarComando(@MemoryId String id, @UserMessage String pergunta);

    // ==========================================
    // SKILL 1: RESPOSTA TEXTUAL 
    // ==========================================
    @SystemMessage({
        "Você é o proIAp, o assistente virtual oficial da PROAP.",
        "Use APENAS o CONTEXTO fornecido abaixo para responder.",
        "CONTEXTO: {{contexto}}",
        "REGRA DE OURO: Se a resposta não estiver no contexto acima, diga exatamente: 'Desculpe, não encontrei essa informação nos documentos institucionais acessíveis no momento.'",
        "NUNCA crie respostas baseadas em seu conhecimento prévio, apenas no CONTEXTO.",
        "REGRA DE SEGURANÇA: Nunca mencione intenções internas, GRAFICO, ou PAINEL."
    })
    String responderDuvida(@UserMessage String pergunta, @V("contexto") String contexto);

    // ==========================================
    // SKILL 2: GERADOR DE GRÁFICOS 
    // ==========================================
    @SystemMessage({
        "Você é um especialista em visualização de dados da PROAP.",
        "Sua tarefa é ler os DADOS RECUPERADOS e montar a configuração do gráfico ECharts.",
        "DADOS RECUPERADOS: {{dados_recuperados}}",
        "MEMÓRIA DA SESSÃO ATUAL:",
        "- Indicador Focado: {{indicador}}",
        "- Tipo de Gráfico Preferido: {{tipo_grafico}}",
        "REGRA 1: Use EXATAMENTE os números fornecidos. NUNCA invente ou deduza valores.",
        "REGRA 2: Extraia a métrica exata que o usuário pediu para TODOS os anos disponíveis.",
        "REGRA 3: O campo 'tipo' do json DEVE respeitar o 'Tipo de Gráfico Preferido' da memória da sessão, a menos que o usuário exija outro na mensagem.",
        "REGRA 4: Se os números exatos para o gráfico solicitado não estiverem nos DADOS RECUPERADOS, retorne as séries vazias e avise.",
        "REGRA 5: Preencha o 'mensagemContexto' com uma frase natural e amigável dizendo os dados que encontrou e perguntando se o usuário quer ver o gráfico. NUNCA termine com dois pontos (:).",
        "Retorne estritamente o objeto estruturado."
    })
    GraficoSpec gerarGrafico(
            @UserMessage String pergunta, 
            @V("dados_recuperados") String dadosRecuperados,
            @V("indicador") String indicadorSessao,
            @V("tipo_grafico") String tipoGraficoSessao
        );

    // ==========================================
    // SKILL 3: CLASSIFICADOR DE INTENÇÃO RÁPIDA
    // ==========================================
    @SystemMessage({
        "Você é um classificador de intenções.",
        "O sistema perguntou se o usuário 'Confere?' os dados ou ele está aguardando um gráfico.",
        "Leia a resposta do usuário e classifique-a em uma destas 3 categorias EXATAS:",
        "CONFIRMAR - se ele aceitou, disse sim, pediu para mostrar, enviou um 'cadê', 'manda', 'confere', etc.",
        "NEGAR - se ele disse não, que está errado, pediu para cancelar, mudar, etc.",
        "NOVA_PERGUNTA - se ele ignorou a confirmação e fez uma pergunta ou pedido completamente diferente.",
        "Retorne ESTRITAMENTE uma única palavra (CONFIRMAR, NEGAR ou NOVA_PERGUNTA). NUNCA adicione pontos finais, aspas ou explicações."
    })
    String classificarConfirmacao(@MemoryId String id, @UserMessage String respostaUsuario);
}