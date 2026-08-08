package com.bicentral.bicentral_backend.service.ia;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DesempenhoDepartamentoTool {

    private final JdbcTemplate jdbcTemplate;

    public DesempenhoDepartamentoTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirView();
    }

    private void garantirView() {
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW pat_execucao_departamento AS
            SELECT
                p.departamento,
                substring(p.dados_completos->>'Título' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                p.dados_completos->>'Título' AS titulo_acao,
                NULLIF(replace(p.dados_completos->>'%', ',', '.'), '')::numeric / 100 AS percentual_execucao,
                gd.tipo_unidade
            FROM pat_dados p
            LEFT JOIN (
                SELECT DISTINCT departamento, tipo_unidade FROM gerentes_departamento
            ) gd ON gd.departamento = p.departamento
            """);
    }

    @Tool("Ranqueia departamentos pela média de execução do PAT (ano corrente). Use para perguntas sobre quais unidades estão melhores ou piores no PAT. Pode filtrar por UG ou UA.")
    public String ranquearDepartamentosPorExecucaoPAT(
            @P("'melhores' para maior execução primeiro, 'piores' para menor execução primeiro") String ordem,
            @P(value = "'UG' para só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade a retornar, padrão 10", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : limite;
        String direcao = ordem != null && ordem.toLowerCase().contains("melhor") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        System.out.println(">>> TOOL CHAMADA: ranquearDepartamentosPorExecucaoPAT(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        String sql = "SELECT departamento, ROUND(AVG(percentual_execucao) * 100, 2) AS media_execucao_pct, COUNT(*) AS qtd_acoes " +
                     "FROM pat_execucao_departamento " +
                     (filtrarTipo ? "WHERE tipo_unidade = ? " : "") +
                     "GROUP BY departamento " +
                     "ORDER BY media_execucao_pct " + direcao + " " +
                     "LIMIT ?";

        List<Map<String, Object>> resultado = filtrarTipo
            ? jdbcTemplate.queryForList(sql, tipoUnidade.toUpperCase(), qtd)
            : jdbcTemplate.queryForList(sql, qtd);

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " departamento(s) retornado(s)");
        if (resultado.isEmpty()) {
            return "Nenhum departamento encontrado no PAT do ano corrente.";
        }
        return "Ranking de departamentos por execução média do PAT (ano corrente):\n" + formatarResultado(resultado);
    }

    @Tool("Busca as ações com MENOR execução do PAT (ano corrente) de um departamento específico, até 15 ações. Use para perguntas pontuais tipo 'quais ações estão mais atrasadas na PROEST'. Para pedidos de RELATÓRIO ou PANORAMA completo de uma unidade, use buscarDetalhamentoDesempenhoDepartamento em vez desta.")
    public String buscarExecucaoPATPorDepartamento(@P("nome ou parte do nome do departamento") String nomeDepartamento) {
        System.out.println(">>> TOOL CHAMADA: buscarExecucaoPATPorDepartamento(nome=" + nomeDepartamento + ")");
        String sql = "SELECT codigo_acao, titulo_acao, tipo_unidade, ROUND(percentual_execucao * 100, 2) AS percentual_pct " +
                     "FROM pat_execucao_departamento " +
                     "WHERE departamento ILIKE ? " +
                     "ORDER BY percentual_execucao ASC " +
                     "LIMIT 15";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, "%" + nomeDepartamento.trim() + "%");
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhum registro de PAT encontrado para o departamento '" + nomeDepartamento + "'";
        }
        return resultado.size() >= 15
            ? "Mostrando as 15 ações com menor execução (pode haver mais). Para um panorama completo com resumo, use a ferramenta de relatório.\n" + formatarResultado(resultado)
            : formatarResultado(resultado);
    }

    @Tool("Busca um RESUMO/RELATÓRIO/PANORAMA do desempenho do PAT (ano corrente) de um departamento: contagem de ações por faixa de execução (zeradas, em andamento, concluídas), média geral, e as ações nos extremos (mais atrasadas e mais adiantadas). Use esta ferramenta sempre que o usuário pedir 'relatório', 'panorama' ou 'análise' de uma unidade. NÃO tire conclusões de 'bom' ou 'ruim' sozinho a partir do número bruto, a interpretação cabe a você considerando a natureza de cada ação.")
    public String buscarDetalhamentoDesempenhoDepartamento(@P("nome ou parte do nome do departamento") String nomeDepartamento) {
        System.out.println(">>> TOOL CHAMADA: buscarDetalhamentoDesempenhoDepartamento(nome=" + nomeDepartamento + ")");

        String sqlResumo = """
            SELECT
                COUNT(*) AS total_acoes,
                ROUND(AVG(percentual_execucao) * 100, 2) AS media_geral,
                COUNT(*) FILTER (WHERE percentual_execucao = 0) AS zeradas,
                COUNT(*) FILTER (WHERE percentual_execucao > 0 AND percentual_execucao < 1) AS em_andamento,
                COUNT(*) FILTER (WHERE percentual_execucao >= 1) AS concluidas
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            """;
        Map<String, Object> resumo;
        try {
            resumo = jdbcTemplate.queryForMap(sqlResumo, "%" + nomeDepartamento.trim() + "%");
        } catch (Exception e) {
            return "Nenhuma ação de PAT encontrada para o departamento '" + nomeDepartamento + "'";
        }

        Object totalObj = resumo.get("total_acoes");
        if (totalObj == null || ((Number) totalObj).intValue() == 0) {
            return "Nenhuma ação de PAT encontrada para o departamento '" + nomeDepartamento + "'";
        }

        String sqlPiores = """
            SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual_pat
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            ORDER BY percentual_execucao ASC
            LIMIT 8
            """;
        String sqlMelhores = """
            SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual_pat
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            ORDER BY percentual_execucao DESC
            LIMIT 8
            """;

        List<Map<String, Object>> piores = jdbcTemplate.queryForList(sqlPiores, "%" + nomeDepartamento.trim() + "%");
        List<Map<String, Object>> melhores = jdbcTemplate.queryForList(sqlMelhores, "%" + nomeDepartamento.trim() + "%");

        System.out.println(">>> TOOL RESULTADO: total=" + totalObj + ", resumo agregado + 8 piores + 8 melhores");

        StringBuilder sb = new StringBuilder();
        sb.append("RESUMO GERAL: ").append(totalObj).append(" ações no total, média de execução ")
          .append(resumo.get("media_geral")).append("%. ")
          .append(resumo.get("zeradas")).append(" ações em 0%, ")
          .append(resumo.get("em_andamento")).append(" em andamento, ")
          .append(resumo.get("concluidas")).append(" concluídas (100%).\n\n");

        sb.append("AÇÕES COM MENOR EXECUÇÃO (até 8):\n");
        sb.append(formatarResultado(piores));

        sb.append("\nAÇÕES COM MAIOR EXECUÇÃO (até 8):\n");
        sb.append(formatarResultado(melhores));

        return sb.toString();
    }

    @Tool("Conta quantas ações do PAT (ano corrente) cada departamento tem, ranqueando por quantidade. Use para perguntas tipo 'qual UG tem menos/mais ações no PAT'. Permite filtrar só por UG ou só por UA.")
    public String contarAcoesPorDepartamentoPAT(
            @P("'menos' para ranquear do menor para o maior número de ações, 'mais' para o maior primeiro") String ordem,
            @P(value = "'UG' para filtrar só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade de departamentos a retornar, padrão 10", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : limite;
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        System.out.println(">>> TOOL CHAMADA: contarAcoesPorDepartamentoPAT(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        String sql = "SELECT departamento, tipo_unidade, COUNT(*) AS qtd_acoes " +
                     "FROM pat_execucao_departamento " +
                     (filtrarTipo ? "WHERE tipo_unidade = ? " : "") +
                     "GROUP BY departamento, tipo_unidade " +
                     "ORDER BY qtd_acoes " + direcao + " " +
                     "LIMIT ?";

        List<Map<String, Object>> resultado = filtrarTipo
            ? jdbcTemplate.queryForList(sql, tipoUnidade.toUpperCase(), qtd)
            : jdbcTemplate.queryForList(sql, qtd);

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " departamento(s) retornado(s)");
        if (resultado.isEmpty()) {
            return "Nenhum departamento encontrado no PAT.";
        }
        return "Contagem de ações do PAT por departamento" + (filtrarTipo ? " (filtrado por " + tipoUnidade.toUpperCase() + ")" : "") + ":\n" + formatarResultado(resultado);
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