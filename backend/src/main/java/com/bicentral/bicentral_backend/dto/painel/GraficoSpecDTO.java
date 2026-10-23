package com.bicentral.bicentral_backend.dto.painel;

import java.util.List;
import dev.langchain4j.model.output.structured.Description;

<<<<<<< HEAD
public record GraficoSpecDTO(
    
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
    List<SerieGraficoDTO> series,
    
    @Description("true se ainda está aguardando confirmação do usuário, false se já pode renderizar o gráfico")
    Boolean aguardandoConfirmacao
) {}
=======
// Um gráfico individual dentro de um PainelSpecDTO. Antes carregava mensagemContexto/skill/
// aguardandoConfirmacao próprios (fazia sentido quando cada resposta era UM gráfico solto) —
// agora isso mora só no painel, já que a confirmação é do painel inteiro, não de cada gráfico.
public record GraficoSpecDTO(
    @Description("Um título curto e claro para este gráfico")
    String titulo,

    @Description("O tipo do gráfico do ECharts. Opções permitidas: 'bar', 'line', 'pie', 'gauge' (indicador único), 'combo' (barra + linha, para PAT vs PDI), 'empilhado' (barra empilhada, para distribuição de status entre categorias)")
    String tipo,

    @Description("Os rótulos do eixo X (categorias). Ex: ['Engenharia', 'Computação', 'Direito']")
    List<String> eixoX,

    @Description("A lista de séries de dados reais extraídos do contexto para popular o gráfico")
    List<SerieGraficoDTO> series
) {}
>>>>>>> d7f41a2f7ee04ff86c43972e609226b3fc0f6bb5
