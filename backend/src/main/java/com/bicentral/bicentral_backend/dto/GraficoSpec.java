package com.bicentral.bicentral_backend.dto;

import java.util.List;
import dev.langchain4j.model.output.structured.Description;

public record GraficoSpec(
    
    @Description("Crie uma mensagem natural, amigável e SEMPRE VARIADA. Informe os dados principais que você encontrou de forma resumida e pergunte se o usuário confirma a geração do gráfico. Termine SEMPRE com um ponto de interrogação (?). NUNCA use dois pontos (:) no final. Exemplo de tom: 'Encontrei os dados de X e Y no documento. Posso gerar o gráfico com esses números?'")
    String mensagemContexto,

    @Description("Deve ser SEMPRE a palavra exata: 'grafico'")
    String skill,
    
    @Description("Um título curto e claro para o gráfico")
    String titulo,
    
    @Description("O tipo do gráfico do ECharts. Opções permitidas: 'bar', 'line', 'pie'")
    String tipo,
    
    @Description("Os rótulos do eixo X (categorias). Ex: ['Engenharia', 'Computação', 'Direito']")
    List<String> eixoX,
    
    @Description("A lista de séries de dados reais extraídos do contexto para popular o gráfico")
    List<SerieGrafico> series,
    
    @Description("true se ainda está aguardando confirmação do usuário, false se já pode renderizar o gráfico")
    Boolean aguardandoConfirmacao
) {}