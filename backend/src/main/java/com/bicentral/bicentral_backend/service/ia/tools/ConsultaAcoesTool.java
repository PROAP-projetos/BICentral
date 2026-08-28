package com.bicentral.bicentral_backend.service.ia.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ConsultaAcoesTool {

    private final JdbcTemplate jdbcTemplate;

    public ConsultaAcoesTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        garantirTabelaDepartamentoTipo();
        garantirView();
        preencherClassificacaoPadrao();
    }

    // Duplicado do guard em AdminService (mesma tabela) — a ordem de inicialização dos beans
    // não é garantida, e a view abaixo depende dessa tabela já existir.
    private void garantirTabelaDepartamentoTipo() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS departamento_tipo (
                departamento TEXT PRIMARY KEY,
                tipo_unidade VARCHAR(2) NOT NULL,
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
    }

    private void garantirView() {
        jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW pat_execucao_departamento AS
            SELECT
                p.departamento,
                substring(p.dados_completos->>'Título' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                p.dados_completos->>'Título' AS titulo_acao,
                NULLIF(replace(p.dados_completos->>'%', ',', '.'), '')::numeric / 100 AS percentual_execucao,
                COALESCE(dt.tipo_unidade, gd.tipo_unidade) AS tipo_unidade
            FROM pat_dados p
            LEFT JOIN departamento_tipo dt ON dt.departamento = p.departamento
            LEFT JOIN (
                SELECT DISTINCT departamento, tipo_unidade FROM gerentes_departamento
            ) gd ON gd.departamento = p.departamento
            """);

        // CREATE OR REPLACE VIEW não garante essa opção sozinho — sem ela, a view roda com o
        // privilégio de quem a criou, driblando o RLS de pat_dados pra quem consulta via API anônima.
        jdbcTemplate.execute("ALTER VIEW pat_execucao_departamento SET (security_invoker = true)");
    }

    // Classificação automática pros casos óbvios pelo nome (Coordenação = UA, Campus = UA) que
    // ainda não foram classificados nem por departamento_tipo nem por gerentes_departamento.
    // Roda toda inicialização, mas só insere o que ainda falta (ON CONFLICT DO NOTHING) — não
    // sobrescreve nenhuma classificação manual já feita, seja aqui ou via gerente.
    private void preencherClassificacaoPadrao() {
        int inseridos = jdbcTemplate.update("""
            INSERT INTO departamento_tipo (departamento, tipo_unidade)
            SELECT DISTINCT p.departamento, 'UA'
            FROM pat_dados p
            WHERE (p.departamento ILIKE 'Coord%' OR p.departamento ILIKE 'Campus%')
              AND NOT EXISTS (SELECT 1 FROM departamento_tipo dt WHERE dt.departamento = p.departamento)
              AND NOT EXISTS (
                  SELECT 1 FROM gerentes_departamento gd
                  WHERE gd.departamento = p.departamento AND gd.tipo_unidade IS NOT NULL
              )
            ON CONFLICT (departamento) DO NOTHING
            """);
        if (inseridos > 0) {
            System.out.println(">>> CLASSIFICAÇÃO PADRÃO: " + inseridos + " departamento(s) classificado(s) automaticamente como UA (Coordenação/Campus sem classificação prévia)");
        }
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
        return formatarItemUnico(resultado.get(0));
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
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : resultado) {
            sb.append("- [").append(item.get("codigo")).append("] ").append(item.get("titulo"))
              .append(" (").append(item.get("estrutura")).append(")\n");
        }
        return sb.toString();
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
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : resultado) {
            sb.append("- [").append(item.get("codigo")).append("] ").append(item.get("titulo"))
              .append(" — período: ").append(item.get("data_inicial")).append(" a ").append(item.get("data_final")).append("\n");
        }
        return sb.toString();
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
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : resultado) {
            sb.append("- [").append(item.get("codigo")).append("] ").append(item.get("titulo"))
              .append(" — depto: ").append(item.get("departamentos"))
              .append(" — ").append(formatarPercentualEnxuto(item.get("percentual_pdi"))).append("% no PDI\n");
        }
        return sb.toString();
    }

    @Tool("Ranqueia departamentos pela média de execução do PAT (ano corrente). Use para perguntas sobre quais unidades estão melhores ou piores no PAT. Pode filtrar por UG ou UA.")
    public String ranquearDepartamentosPorExecucaoPAT(
            @P("'melhores' para maior execução primeiro, 'piores' para menor execução primeiro") String ordem,
            @P(value = "'UG' para só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade a retornar, padrão 10. Se o usuário pedir 'todos os departamentos' ou não quiser recorte nenhum, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
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
        StringBuilder sb = new StringBuilder();
        sb.append("Ranking de departamentos por execução média do PAT (ano corrente):\n\n");
        sb.append("| Departamento | % Execução | Qtd Ações |\n");
        sb.append("|---|---|---|\n");
        for (Map<String, Object> item : resultado) {
            sb.append("| ").append(item.get("departamento"))
              .append(" | ").append(formatarPercentualEnxuto(item.get("media_execucao_pct"))).append("%")
              .append(" | ").append(item.get("qtd_acoes"))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Busca as ações com MENOR execução do PAT (ano corrente) de um departamento específico, até 15 ações. Use para perguntas pontuais tipo 'quais ações estão mais atrasadas na PROEST'. Para pedidos de RELATÓRIO ou PANORAMA completo de uma unidade, use buscarDetalhamentoDesempenhoDepartamento em vez desta.")
    public String buscarExecucaoPATPorDepartamento(@P("nome ou parte do nome do departamento") String nomeDepartamento) {
        System.out.println(">>> TOOL CHAMADA: buscarExecucaoPATPorDepartamento(nome=" + nomeDepartamento + ")");
        String sql = "SELECT titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual_pct " +
                     "FROM pat_execucao_departamento " +
                     "WHERE departamento ILIKE ? " +
                     "ORDER BY percentual_execucao ASC " +
                     "LIMIT 15";
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, "%" + nomeDepartamento.trim() + "%");
        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " linha(s) encontrada(s)");
        if (resultado.isEmpty()) {
            return "Nenhum registro de PAT encontrado para o departamento '" + nomeDepartamento + "'";
        }
        StringBuilder sb = new StringBuilder();
        if (resultado.size() >= 15) {
            sb.append("Mostrando as 15 ações com menor execução (pode haver mais). Para um panorama completo com resumo, use a ferramenta de relatório.\n\n");
        }
        // Sem coluna de código nem de unidade: titulo_acao já vem com o código colado na frente
        // (repeti-lo numa coluna própria era redundante), e "unidade" é sempre a mesma aqui porque
        // a busca já é filtrada por um departamento só — não agrega informação nenhuma pro usuário.
        sb.append("| Ação | % |\n");
        sb.append("|---|---|\n");
        for (Map<String, Object> item : resultado) {
            sb.append("| ").append(truncarTitulo((String) item.get("titulo_acao")))
              .append(" | ").append(formatarPercentualEnxuto(item.get("percentual_pct"))).append("%")
              .append(" |\n");
        }
        return sb.toString();
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
            SELECT codigo_acao, titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual_pat
            FROM pat_execucao_departamento
            WHERE departamento ILIKE ?
            ORDER BY percentual_execucao ASC
            LIMIT 8
            """;
        String sqlMelhores = """
            SELECT codigo_acao, titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual_pat
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
        for (Map<String, Object> item : piores) {
            sb.append("- [").append(formatarCodigo(item.get("codigo_acao"))).append("] ").append(truncarTitulo((String) item.get("titulo_acao"))).append(" — ").append(formatarPercentualEnxuto(item.get("percentual_pat"))).append("%\n");
        }

        sb.append("\nAÇÕES COM MAIOR EXECUÇÃO (até 8):\n");
        for (Map<String, Object> item : melhores) {
            sb.append("- [").append(formatarCodigo(item.get("codigo_acao"))).append("] ").append(truncarTitulo((String) item.get("titulo_acao"))).append(" — ").append(formatarPercentualEnxuto(item.get("percentual_pat"))).append("%\n");
        }

        return sb.toString();
    }

    // titulo_acao vem do dado bruto e traz o nome completo do departamento colado após " | " —
    // redundante aqui porque as duas listas já são de um único departamento (o parâmetro da busca),
    // e repetir esse texto 16x infla desnecessariamente o tamanho da resposta (relevante pro TPM do Groq).
    private String truncarTitulo(String tituloAcao) {
        if (tituloAcao == null) return "";
        int separador = tituloAcao.indexOf(" | ");
        return separador >= 0 ? tituloAcao.substring(0, separador) : tituloAcao;
    }

    @Tool("Conta quantas ações do PDI cada departamento/UG é responsável, ranqueando por quantidade. Use para perguntas tipo 'qual UG tem menos/mais ações no PDI'. Uma ação pode ter vários departamentos responsáveis; esta ferramenta conta corretamente cada departamento separadamente. Permite filtrar só por UG ou só por UA, usando a classificação já conhecida do PAT.")
    public String contarAcoesPorDepartamentoPDI(
            @P("'menos' para ranquear do menor para o maior número de ações, 'mais' para o maior primeiro") String ordem,
            @P(value = "'UG' para filtrar só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade de departamentos a retornar, padrão 10. Se o usuário pedir 'todos os departamentos' ou não quiser recorte nenhum, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        System.out.println(">>> TOOL CHAMADA: contarAcoesPorDepartamentoPDI(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        String sql = """
            WITH pdi_deptos AS (
                SELECT TRIM(depto) AS departamento, COUNT(*) AS qtd_acoes
                FROM acoes_pdi, unnest(string_to_array(departamentos, ';')) AS depto
                WHERE estrutura = 'Ação' AND departamentos IS NOT NULL
                GROUP BY TRIM(depto)
            ),
            mapeamento AS (
                SELECT DISTINCT departamento, tipo_unidade FROM pat_execucao_departamento
            )
            SELECT p.departamento, m.tipo_unidade, p.qtd_acoes
            FROM pdi_deptos p
            LEFT JOIN mapeamento m ON m.departamento = p.departamento
            """ + (filtrarTipo ? "WHERE m.tipo_unidade = ? " : "") + """
            ORDER BY p.qtd_acoes """ + direcao + """
            LIMIT ?
            """;

        List<Map<String, Object>> resultado = filtrarTipo
            ? jdbcTemplate.queryForList(sql, tipoUnidade.toUpperCase(), qtd)
            : jdbcTemplate.queryForList(sql, qtd);

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " departamento(s) retornado(s)");
        if (resultado.isEmpty()) {
            return "Nenhum departamento encontrado nas ações do PDI.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Contagem de ações do PDI por departamento")
          .append(filtrarTipo ? " (filtrado por " + tipoUnidade.toUpperCase() + ")" : ", tipo pode vir nulo se o departamento não aparecer no PAT")
          .append(":\n\n");
        sb.append("| Departamento | Tipo | Qtd Ações |\n");
        sb.append("|---|---|---|\n");
        for (Map<String, Object> item : resultado) {
            sb.append("| ").append(item.get("departamento"))
              .append(" | ").append(item.get("tipo_unidade"))
              .append(" | ").append(item.get("qtd_acoes"))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Conta quantas ações do PAT (ano corrente) cada departamento tem, ranqueando por quantidade. Use para perguntas tipo 'qual UG tem menos/mais ações no PAT'. Permite filtrar só por UG ou só por UA.")
    public String contarAcoesPorDepartamentoPAT(
            @P("'menos' para ranquear do menor para o maior número de ações, 'mais' para o maior primeiro") String ordem,
            @P(value = "'UG' para filtrar só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade de departamentos a retornar, padrão 10. Se o usuário pedir 'todos os departamentos' ou não quiser recorte nenhum, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        System.out.println(">>> TOOL CHAMADA: contarAcoesPorDepartamentoPAT(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));

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
        StringBuilder sb = new StringBuilder();
        sb.append("Contagem de ações do PAT por departamento")
          .append(filtrarTipo ? " (filtrado por " + tipoUnidade.toUpperCase() + ")" : "")
          .append(":\n\n");
        sb.append("| Departamento | Tipo | Qtd Ações |\n");
        sb.append("|---|---|---|\n");
        for (Map<String, Object> item : resultado) {
            sb.append("| ").append(item.get("departamento"))
              .append(" | ").append(item.get("tipo_unidade"))
              .append(" | ").append(item.get("qtd_acoes"))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Compara a execução de uma mesma ação entre o PDI (acumulado dos 5 anos) e o PAT (ano corrente), usando o código da ação. Use quando o usuário quiser entender se uma ação está adiantada ou atrasada em relação ao plano de longo prazo.")
    public String compararExecucaoPDIxPAT(@P("código exato da ação, ex: 1.1.1.3") String codigo) {
        System.out.println(">>> TOOL CHAMADA: compararExecucaoPDIxPAT(codigo=" + codigo + ")");

        String sqlPdi = "SELECT titulo, percentual_pdi FROM acoes_pdi WHERE codigo = ? AND estrutura = 'Ação'";
        List<Map<String, Object>> pdiResultado = jdbcTemplate.queryForList(sqlPdi, codigo.trim());

        if (pdiResultado.isEmpty()) {
            return "Nenhuma ação encontrada no PDI com o código " + codigo;
        }

        String titulo = (String) pdiResultado.get(0).get("titulo");
        Object percentualPdiObj = pdiResultado.get(0).get("percentual_pdi");

        String sqlPat = """
            SELECT ROUND(AVG(percentual_execucao) * 100, 2) AS media_pat, COUNT(DISTINCT departamento) AS qtd_departamentos
            FROM pat_execucao_departamento
            WHERE codigo_acao = ?
            """;
        Map<String, Object> patResultado;
        try {
            patResultado = jdbcTemplate.queryForMap(sqlPat, codigo.trim());
        } catch (Exception e) {
            patResultado = null;
        }

        System.out.println(">>> TOOL RESULTADO: comparação montada para código " + codigo);

        StringBuilder sb = new StringBuilder();
        sb.append("Ação: ").append(titulo).append(" (código ").append(codigo).append(")\n");
        sb.append("PDI (acumulado 2026-2030): ").append(formatarPercentualEnxuto(percentualPdiObj)).append("%\n");

        Object mediaPatObj = patResultado != null ? patResultado.get("media_pat") : null;
        if (mediaPatObj != null) {
            sb.append("PAT (ano corrente): ").append(formatarPercentualEnxuto(mediaPatObj)).append("% de execução média, distribuída entre ")
              .append(patResultado.get("qtd_departamentos")).append(" departamento(s) responsável(is).");
        } else {
            sb.append("PAT (ano corrente): nenhum registro de execução encontrado para esta ação.");
        }

        return sb.toString();
    }

    private static final double GAP_GARGALO_PONTOS = 30.0;
    private static final int MAX_LINHAS_GARGALO = 20;

    @Tool("Compara a execução de uma ação do PAT (ano corrente) entre TODOS os departamentos que a compartilham, e aponta qual unidade está significativamente mais atrasada que as outras na mesma ação. Use quando o usuário perguntar por que uma ação compartilhada está mal executada, ou qual unidade específica está travando/puxando pra baixo uma ação que outros departamentos também respondem. Precisa do código exato da ação — se não tiver, busque primeiro com outra ferramenta (ex: buscarExecucaoPATPorDepartamento) pra descobrir o código.")
    public String rastrearGargaloEmAcaoCompartilhada(@P("código da ação, com ou sem a letra na frente — ex: 'U 5.1.8.6' ou só '5.1.8.6'") String codigoAcao) {
        System.out.println(">>> TOOL CHAMADA: rastrearGargaloEmAcaoCompartilhada(codigoAcao=" + codigoAcao + ")");

        // Usuário raramente digita a letra que antecede o código (ex: "U 5.1.8.6") — compara só
        // a parte numérica, tirando qualquer letra+espaço tanto do valor salvo quanto do que veio.
        String sql = """
            SELECT codigo_acao, departamento, titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual
            FROM pat_execucao_departamento
            WHERE regexp_replace(codigo_acao, '^[A-Za-zÀ-ÿ]+\\s*', '') = regexp_replace(?, '^[A-Za-zÀ-ÿ]+\\s*', '')
            ORDER BY percentual_execucao DESC
            """;
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, codigoAcao.trim());

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " departamento(s) compartilhando a ação " + codigoAcao);

        if (resultado.isEmpty()) {
            return "Nenhum registro de PAT encontrado para a ação " + codigoAcao;
        }
        String codigoReal = formatarCodigo(resultado.get(0).get("codigo_acao"));
        if (resultado.size() == 1) {
            return "A ação " + codigoReal + " não é compartilhada: só o departamento '" + resultado.get(0).get("departamento")
                 + "' responde por ela, com " + formatarPercentualEnxuto(resultado.get(0).get("percentual")) + "% de execução.";
        }

        double media = resultado.stream()
            .mapToDouble(item -> ((Number) item.get("percentual")).doubleValue())
            .average().orElse(0.0);
        String tituloAcao = truncarTitulo((String) resultado.get(0).get("titulo_acao"));

        boolean truncado = resultado.size() > MAX_LINHAS_GARGALO;
        List<Map<String, Object>> exibidos = truncado ? montarAmostraGargalo(resultado) : resultado;

        StringBuilder sb = new StringBuilder();
        sb.append("Ação ").append(codigoReal);
        if (!tituloAcao.isBlank()) sb.append(" — ").append(tituloAcao);
        sb.append(" — compartilhada entre ").append(resultado.size()).append(" departamentos");
        if (truncado) sb.append(" (mostrando os 5 melhores e os 15 piores)");
        sb.append(":\n\n");
        sb.append("| Departamento | % Execução |\n");
        sb.append("|---|---|\n");
        for (Map<String, Object> item : exibidos) {
            double percentual = ((Number) item.get("percentual")).doubleValue();
            double gap = media - percentual;
            String sinalizacao = gap >= GAP_GARGALO_PONTOS ? " ⚠️ bem abaixo da média do grupo" : "";
            sb.append("| ").append(item.get("departamento"))
              .append(" | ").append(formatarPercentualEnxuto(percentual)).append("%").append(sinalizacao)
              .append(" |\n");
        }
        sb.append("\nMédia de execução do grupo: ").append(formatarPercentualEnxuto(media)).append("%.");
        return sb.toString();
    }

    /** 100.00 vira "100", mas 36.52 continua "36.52" — só mostra casa decimal quando o número não é inteiro. */
    private String formatarPercentualEnxuto(double valor) {
        return valor == Math.rint(valor) ? String.format("%.0f", valor) : String.format("%.2f", valor);
    }

    /** Sobrecarga null-safe pra usar direto com o valor cru vindo do banco (Number ou null). */
    private String formatarPercentualEnxuto(Object valor) {
        return valor == null ? "—" : formatarPercentualEnxuto(((Number) valor).doubleValue());
    }

    /** Espaço normal em "U 2.1.2.22" deixa o navegador quebrar linha bem no meio do código dentro
     * de coluna estreita — troca por espaço não-quebrável pra o código sempre ficar numa linha só. */
    private String formatarCodigo(Object codigo) {
        return codigo == null ? "" : codigo.toString().replace(' ', ' ');
    }

    /** Amostra representativa pra grupos grandes: os 5 melhores + os 15 piores (a lista já vem ordenada DESC). */
    private List<Map<String, Object>> montarAmostraGargalo(List<Map<String, Object>> ordenadoDesc) {
        List<Map<String, Object>> amostra = new ArrayList<>(ordenadoDesc.subList(0, 5));
        amostra.addAll(ordenadoDesc.subList(ordenadoDesc.size() - 15, ordenadoDesc.size()));
        return amostra;
    }

    private String formatarItemUnico(Map<String, Object> item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.get("estrutura")).append(" ").append(item.get("codigo")).append(": ").append(item.get("titulo")).append(".\n");
        if (item.get("departamentos") != null) {
            sb.append("Departamento(s): ").append(item.get("departamentos")).append(".\n");
        }
        if (item.get("percentual_pdi") != null) {
            sb.append("Execução no PDI: ").append(formatarPercentualEnxuto(item.get("percentual_pdi"))).append("%.\n");
        }
        if (item.get("data_inicial") != null || item.get("data_final") != null) {
            sb.append("Período: ").append(item.get("data_inicial")).append(" a ").append(item.get("data_final")).append(".\n");
        }
        return sb.toString();
    }
}