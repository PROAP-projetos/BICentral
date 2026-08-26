package com.bicentral.bicentral_backend.dto.ia;

import dev.langchain4j.model.output.structured.Description;

public record AnaliseComandoDTO(

    @Description("A intenção do usuário. Escolha GRAFICO para visuais numéricos/gráficos, PAINEL para dashboards, ou RESPOSTA para dúvidas textuais.")
    IntencaoDTO intencao,

    @Description("O formato visual do gráfico. Ex: 'bar', 'pie', 'line'. Se não for mencionado, retorne nulo.")
    String tipoGrafico,

    @Description("A métrica ou indicador principal. Ex: 'Matrículas', 'Evasão'. Se não for mencionado, retorne nulo.")
    String indicador,

    @Description("O ano de referência para os dados. Retorne APENAS um número inteiro simples como string. Ex: '2023', '2024'. Se não for mencionado, retorne nulo.")
    String ano,  // ← String em vez de Integer

    @Description("O nome do curso específico. Ex: 'Engenharia', 'Medicina'. Se disser 'todos os cursos', retorne 'Todos'. Se não mencionar, retorne nulo.")
    String curso

) {}