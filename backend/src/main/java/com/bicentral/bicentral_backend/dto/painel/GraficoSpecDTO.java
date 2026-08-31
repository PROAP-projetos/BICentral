package com.bicentral.bicentral_backend.dto.painel;

import java.util.List;
import dev.langchain4j.model.output.structured.Description;

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
