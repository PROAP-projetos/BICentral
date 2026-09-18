package com.bicentral.bicentral_backend.dto.painel;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

// Formato único que a IA sempre devolve pra qualquer pedido de visualização — um pedido de "só
// um gráfico" vira um painel com 1 item em `graficos`; um pedido de comparação vira um painel
// com vários. Antes existiam dois formatos separados (GraficoSpecDTO sozinho vs isso aqui, que
// nunca era usado) — unificar evita ter dois caminhos de resposta pra manter.
public record PainelSpecDTO(
    @Description("Deve ser SEMPRE a palavra exata: 'painel'")
    String skill,

    @Description("Se houver dado real nas séries: mensagem natural, amigável e SEMPRE VARIADA, apresentando os dados encontrados — o painel já é exibido direto junto, NUNCA pergunte se o usuário confirma ou quer ver. Se as séries vierem vazias (dado não encontrado): explicação curta do que faltou. Nos dois casos, NUNCA use dois pontos (:) no final.")
    String mensagemContexto,

    @Description("O título geral do painel — se for um único gráfico, pode repetir o título dele")
    String titulo,

    @Description("Lista de gráficos que compõem o painel. Normalmente 1 item; use mais de 1 só quando o pedido envolver comparar vários indicadores, unidades ou períodos que fazem mais sentido como gráficos separados")
    List<GraficoSpecDTO> graficos
) {}
