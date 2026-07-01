package com.bicentral.bicentral_backend.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConsultaAcoesTool {

    private final JdbcTemplate jdbcTemplate;

    public ConsultaAcoesTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool("Busca um item do PDI (Eixo, Objetivo Estratégico, Objetivo Tático ou Ação) pelo código exato, ex: 1.1.1.3")
    public String buscarPorCodigo(@P("código exato do item, ex: 1.1.1.3") String codigo) {
        System.out.println(">>> TOOL CHAMADA: buscarPorCodigo(codigo=" + codigo + ")");
        String sql = "SELECT codigo, titulo, estrutura, departamentos, percentual_pdi, data_inicial, data_final " +
                     "FROM acoes_pdi WHERE codigo = ?";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, codigo.trim());
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhum item encontrado com o código " + codigo;
        }
        return formatarResultado(resultado);
    }

    @Tool("Lista todos os itens filhos diretos de um código pai no PDI. Ex: código pai 1.1.1 retorna as ações 1.1.1.1, 1.1.1.2 etc")
    public String buscarFilhosPorCodigoPai(@P("código pai, ex: 1.1.1") String codigoPai) {
        System.out.println(">>> TOOL CHAMADA: buscarFilhosPorCodigoPai(codigoPai=" + codigoPai + ")");
        String sql = "SELECT codigo, titulo, estrutura FROM acoes_pdi WHERE codigo_pai = ? ORDER BY codigo";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, codigoPai.trim());
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhum item filho encontrado para o código " + codigoPai;
        }
        return formatarResultado(resultado);
    }

    @Tool("Busca ações do PDI cuja data final seja um ano específico. Use para perguntas tipo 'existe ação que termina em [ano]?'")
    public String buscarPorAnoFinal(@P("ano de referência, ex: 2028") int ano) {
        System.out.println(">>> TOOL CHAMADA: buscarPorAnoFinal(ano=" + ano + ")");
        String sql = "SELECT codigo, titulo, data_inicial, data_final FROM acoes_pdi " +
                     "WHERE estrutura = 'Ação' AND data_final = ?";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, ano);
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhuma ação com data final em " + ano + ". Isso não significa erro: pode ser que todas as ações do PDI atual compartilhem o mesmo período.";
        }
        return formatarResultado(resultado);
    }

    @Tool("Conta ações do PDI filtrando por marcador (ex: 'CPA', 'Plano de Governo', 'AUDIN') e, opcionalmente, percentual mínimo de execução")
    public String contarPorMarcador(
            @P("marcador a buscar, ex: CPA") String marcador,
            @P(value = "percentual mínimo de execução (0 a 100), opcional", required = false) Double percentualMinimo) {
        System.out.println(">>> TOOL CHAMADA: contarPorMarcador(marcador=" + marcador + ", percentualMinimo=" + percentualMinimo + ")");
        double minimo = percentualMinimo == null ? 0.0 : percentualMinimo;
        String sql = "SELECT COUNT(*) FROM acoes_pdi WHERE estrutura = 'Ação' AND marcadores ILIKE ? AND percentual_pdi >= ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, "%" + marcador.trim() + "%", minimo);
        System.out.println(">>> TOOL RESULTADO: count=" + count);
        return "Encontradas " + count + " ações com marcador '" + marcador + "'" +
               (percentualMinimo != null ? " e execução >= " + minimo + "%" : "");
    }

    @Tool("Busca ações do PDI por palavra-chave no título, quando o usuário não sabe o código exato")
    public String buscarPorTitulo(@P("palavra-chave a buscar no título da ação") String palavraChave) {
        System.out.println(">>> TOOL CHAMADA: buscarPorTitulo(palavraChave=" + palavraChave + ")");
        String sql = "SELECT codigo, titulo, departamentos, percentual_pdi FROM acoes_pdi " +
                     "WHERE estrutura = 'Ação' AND titulo ILIKE ? LIMIT 10";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, "%" + palavraChave.trim() + "%");
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhuma ação encontrada com o termo '" + palavraChave + "'";
        }
        return formatarResultado(resultado);
    }

    @Tool("Ranqueia Unidades Gestoras (UG) pela média de execução do PAT (Plano Anual de Trabalho) do ano corrente. Use para perguntas sobre quais UGs estão melhores ou piores no PAT/ano atual. NÃO use para perguntas sobre o PDI (execução dos 5 anos) — para isso use outra ferramenta.")
    public String ranquearUnidadesGestorasPorExecucaoPAT(
            @P("'melhores' para maior execução primeiro, 'piores' para menor execução primeiro") String ordem,
            @P(value = "quantidade de unidades a retornar, padrão 10", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : limite;
        String direcao = ordem != null && ordem.toLowerCase().contains("melhor") ? "DESC" : "ASC";
        System.out.println(">>> TOOL CHAMADA: ranquearUnidadesGestorasPorExecucaoPAT(ordem=" + ordem + ", limite=" + qtd + ")");

        String sql = "SELECT departamento, ROUND(AVG(percentual_execucao) * 100, 2) AS media_execucao_pct, COUNT(*) AS qtd_acoes " +
                     "FROM pat_execucao_departamento " +
                     "WHERE tipo_unidade = 'UG' " +
                     "GROUP BY departamento " +
                     "ORDER BY media_execucao_pct " + direcao + " " +
                     "LIMIT ?";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, qtd);
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " unidade(s) retornada(s)");
        if (resultado.isEmpty()) {
            return "Nenhuma Unidade Gestora encontrada no PAT do ano corrente.";
        }
        return "Ranking de Unidades Gestoras (UG) por execução média do PAT (ano corrente, escala percentual):\n" + formatarResultado(resultado);
    }

    @Tool("Busca a execução do PAT (ano corrente) de um departamento específico, detalhando ação por ação. Use quando o usuário perguntar sobre a situação de uma UG ou UA específica.")
    public String buscarExecucaoPATPorDepartamento(@P("nome ou parte do nome do departamento") String nomeDepartamento) {
        System.out.println(">>> TOOL CHAMADA: buscarExecucaoPATPorDepartamento(nome=" + nomeDepartamento + ")");
        String sql = "SELECT codigo_acao, titulo_acao, tipo_unidade, ROUND(percentual_execucao * 100, 2) AS percentual_pct " +
                     "FROM pat_execucao_departamento " +
                     "WHERE departamento ILIKE ? " +
                     "ORDER BY percentual_execucao DESC";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, "%" + nomeDepartamento.trim() + "%");
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhum registro de PAT encontrado para o departamento '" + nomeDepartamento + "'";
        }
        return formatarResultado(resultado);
    }

    private String formatarResultado(List<Map<String, Object>> resultado) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : resultado) {
            row.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
            sb.append("\n");
        }
        return sb.toString();
    }
}