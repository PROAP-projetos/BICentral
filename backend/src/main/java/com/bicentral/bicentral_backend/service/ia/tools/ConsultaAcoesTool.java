package com.bicentral.bicentral_backend.service.ia.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConsultaAcoesTool {

    private final JdbcTemplate jdbcTemplate;
    private final StatusExecucaoAgente statusExecucao;

    public ConsultaAcoesTool(JdbcTemplate jdbcTemplate, StatusExecucaoAgente statusExecucao) {
        this.jdbcTemplate = jdbcTemplate;
        this.statusExecucao = statusExecucao;
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
                COALESCE(dt.tipo_unidade, gd.tipo_unidade) AS tipo_unidade,
                p.dados_completos->>'marcadores' AS marcadores
            FROM pat_dados p
            LEFT JOIN departamento_tipo dt ON dt.departamento = p.departamento
            LEFT JOIN (
                -- gerentes_departamento tem 1 linha por gerente (um departamento pode ter vários),
                -- mas tipo_unidade é atributo do departamento, não do gerente — GROUP BY colapsa
                -- pra 1 linha por departamento. "DISTINCT departamento, tipo_unidade" (versão
                -- anterior) não fazia isso: com um gerente tipado ('UG') e outros sem tipo (NULL),
                -- sobravam 2 linhas distintas pro mesmo departamento, duplicando toda ação dele
                -- nesta view (109 departamentos * cada ação 2x — inclusive nos relatórios gerados).
                -- cast de volta pra VARCHAR(2): CREATE OR REPLACE VIEW não deixa mudar o tipo de
                -- uma coluna já existente, e MAX() sobre VARCHAR(2) devolve varchar sem limite.
                SELECT departamento, MAX(tipo_unidade)::VARCHAR(2) AS tipo_unidade
                FROM gerentes_departamento
                GROUP BY departamento
            ) gd ON gd.departamento = p.departamento
            """);

        // CREATE OR REPLACE VIEW não garante essa opção sozinho — sem ela, a view roda com o
        // privilégio de quem a criou, driblando o RLS de pat_dados pra quem consulta via API anônima.
        jdbcTemplate.execute("ALTER VIEW pat_execucao_departamento SET (security_invoker = true)");
    }

    // Classificação automática pros casos óbvios pelo nome (Coordenação = UA, Campus = UG) que
    // ainda não foram classificados nem por departamento_tipo nem por gerentes_departamento.
    // Roda toda inicialização, mas só insere o que ainda falta (ON CONFLICT DO NOTHING) — não
    // sobrescreve nenhuma classificação manual já feita, seja aqui ou via gerente.
    private void preencherClassificacaoPadrao() {
        int inseridos = jdbcTemplate.update("""
            INSERT INTO departamento_tipo (departamento, tipo_unidade)
            SELECT DISTINCT p.departamento, CASE WHEN p.departamento ILIKE 'Campus%' THEN 'UG' ELSE 'UA' END
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
            System.out.println(">>> CLASSIFICAÇÃO PADRÃO: " + inseridos + " departamento(s) classificado(s) automaticamente (Coordenação = UA, Campus = UG) sem classificação prévia");
        }
    }

    // ---------------------------------------------------------------------------------------
    // FERRAMENTAS DE PDI DESATIVADAS (sem @Tool, não expostas ao agente) — a tabela acoes_pdi
    // hoje é uma carga estática feita à mão, não a integração real da API do PDI, que ainda não
    // foi ligada. O prompt do sistema (AgenteConsultaSql) já instrui o agente a recusar
    // perguntas de PDI — manter essas 6 ferramentas visíveis mesmo assim só infla o schema de
    // function-calling em toda requisição à toa. Código mantido funcional de propósito: quando
    // a integração real acontecer, é só devolver o @Tool(...)
    // (o texto original está comentado logo acima de cada método) e atualizar o prompt.
    // ---------------------------------------------------------------------------------------

    // @Tool("Busca um item do PDI (Eixo, Objetivo Estratégico, Objetivo Tático ou Ação) pelo código exato, ex: 1.1.1.3")
    public String buscarPorCodigo(@P("código exato do item, ex: 1.1.1.3") String codigo) {
        statusExecucao.definir("Buscando o item " + codigo + " no PDI...");
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

    // @Tool("Lista todos os itens filhos diretos de um código pai no PDI. Ex: código pai 1.1.1 retorna as ações 1.1.1.1, 1.1.1.2 etc")
    public String buscarFilhosPorCodigoPai(@P("código pai, ex: 1.1.1") String codigoPai) {
        statusExecucao.definir("Listando os itens do PDI abaixo de " + codigoPai + "...");
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

    // @Tool("Busca ações do PDI cuja data final seja um ano específico. Use para perguntas tipo 'existe ação que termina em [ano]?'")
    public String buscarPorAnoFinal(@P("ano de referência, ex: 2028") int ano) {
        statusExecucao.definir("Procurando ações do PDI que terminam em " + ano + "...");
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

    // @Tool("Conta ações do PDI filtrando por marcador (ex: 'CPA', 'Plano de Governo', 'AUDIN') e, opcionalmente, percentual mínimo de execução")
    public String contarPorMarcador(
            @P("marcador a buscar, ex: CPA") String marcador,
            @P(value = "percentual mínimo de execução (0 a 100), opcional", required = false) Double percentualMinimo) {
        statusExecucao.definir("Contando ações do PDI com o marcador '" + marcador + "'...");
        System.out.println(">>> TOOL CHAMADA: contarPorMarcador(marcador=" + marcador + ", percentualMinimo=" + percentualMinimo + ")");
        double minimo = percentualMinimo == null ? 0.0 : percentualMinimo;
        String sql = "SELECT COUNT(*) FROM acoes_pdi WHERE estrutura = 'Ação' AND marcadores ILIKE ? AND percentual_pdi >= ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, "%" + marcador.trim() + "%", minimo);
        System.out.println(">>> TOOL RESULTADO: count=" + count);
        return "Encontradas " + count + " ações com marcador '" + marcador + "'" +
               (percentualMinimo != null ? " e execução >= " + minimo + "%" : "");
    }

    // @Tool("Busca ações do PDI por palavra-chave no título, quando o usuário não sabe o código exato")
    public String buscarPorTitulo(@P("palavra-chave a buscar no título da ação") String palavraChave) {
        statusExecucao.definir("Buscando ações do PDI com '" + palavraChave + "'...");
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

    @Tool("Ranqueia departamentos pela média de execução do PAT (ano corrente). Use para perguntas sobre quais unidades estão melhores ou piores no PAT. Pode filtrar por UG ou UA e por trecho do nome (ex.: 'Palmas', 'Mestrado', 'Pedagogia'), o que serve para ranquear um grupo de unidades, como todas as coordenações de um campus.")
    public String ranquearDepartamentosPorExecucaoPAT(
            @P("'melhores' para maior execução primeiro, 'piores' para menor execução primeiro") String ordem,
            @P(value = "'UG' para só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade a retornar, padrão 10. Se o usuário pedir 'todos os departamentos' ou não quiser recorte nenhum, passe 500", required = false) Integer limite,
            @P(value = "trecho do nome do departamento para filtrar um grupo (ex.: 'Palmas' traz as coordenações e unidades de Palmas). Combine com tipoUnidade 'UA' para só coordenações. Ao consultar um grupo inteiro, passe limite 500 para não cortar a lista. Deixe null para não filtrar por nome", required = false) String nomeContem) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("melhor") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        statusExecucao.definir("Ranqueando departamentos pela execução do PAT...");
        System.out.println(">>> TOOL CHAMADA: ranquearDepartamentosPorExecucaoPAT(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        List<Object> params = new java.util.ArrayList<>();
        List<String> condicoes = new java.util.ArrayList<>();
        if (filtrarTipo) {
            condicoes.add("tipo_unidade = ?");
            params.add(tipoUnidade.toUpperCase());
        }
        if (nomeContem != null && !nomeContem.isBlank()) {
            condicoes.add("departamento ILIKE ?");
            params.add("%" + nomeContem.trim() + "%");
        }
        params.add(qtd);
        String where = condicoes.isEmpty() ? "" : "WHERE " + String.join(" AND ", condicoes) + " ";

        String sql = "SELECT departamento, ROUND(AVG(percentual_execucao) * 100, 2) AS media_execucao_pct, COUNT(*) AS qtd_acoes " +
                     "FROM pat_execucao_departamento " +
                     where +
                     "GROUP BY departamento " +
                     "ORDER BY media_execucao_pct " + direcao + " " +
                     "LIMIT ?";

        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, params.toArray());

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

    @Tool("Busca e LISTA ações do PAT (ano corrente) marcadas com um marcador/tag específico (ex: 'risco', 'CPA', 'AUDIN', 'Plano de Governo'), opcionalmente filtrando por departamento. Use quando o usuário pedir pra 'relacionar', 'listar' ou 'mostrar' ações com esse marcador — diferente de rankings ou relatórios gerais, que não usam marcador.")
    public String buscarAcoesPorMarcador(
            @P("marcador/tag a buscar, ex: risco, CPA, AUDIN, Plano de Governo") String marcador,
            @P(value = "nome do departamento pra restringir a busca, opcional", required = false) String departamento) {
        boolean filtrarDepto = departamento != null && !departamento.isBlank();
        statusExecucao.definir("Buscando ações com o marcador '" + marcador + "'" + (filtrarDepto ? " em " + departamento : "") + "...");
        System.out.println(">>> TOOL CHAMADA: buscarAcoesPorMarcador(marcador=" + marcador + ", departamento=" + departamento + ")");

        String sql = "SELECT departamento, codigo_acao, titulo_acao, ROUND(percentual_execucao * 100, 2) AS percentual " +
                     "FROM pat_execucao_departamento " +
                     "WHERE marcadores ILIKE ? " +
                     (filtrarDepto ? "AND departamento ILIKE ? " : "") +
                     "ORDER BY departamento, percentual_execucao ASC " +
                     "LIMIT 30";

        List<Map<String, Object>> resultado = filtrarDepto
            ? jdbcTemplate.queryForList(sql, "%" + marcador.trim() + "%", "%" + departamento.trim() + "%")
            : jdbcTemplate.queryForList(sql, "%" + marcador.trim() + "%");

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " ação(ões)");

        if (resultado.isEmpty()) {
            return "Nenhuma ação encontrada com o marcador '" + marcador + "'"
                 + (filtrarDepto ? " no departamento '" + departamento + "'" : "") + ".";
        }

        StringBuilder sb = new StringBuilder();
        if (resultado.size() >= 30) {
            sb.append("Mostrando as 30 primeiras ações com o marcador '").append(marcador).append("' (pode haver mais).\n\n");
        }
        // Departamento só entra na tabela quando a busca não já filtrou por um só — senão toda
        // linha repetiria o mesmo valor à toa (mesmo ajuste feito em contarTarefasPorDepartamento).
        if (filtrarDepto) {
            sb.append("| Ação | Título | % Execução |\n");
            sb.append("|---|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(formatarCodigo(item.get("codigo_acao")))
                  .append(" | ").append(truncarTituloSemCodigo((String) item.get("titulo_acao")))
                  .append(" | ").append(formatarPercentualEnxuto(item.get("percentual"))).append("%")
                  .append(" |\n");
            }
        } else {
            sb.append("| Ação | Título | Departamento | % Execução |\n");
            sb.append("|---|---|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(formatarCodigo(item.get("codigo_acao")))
                  .append(" | ").append(truncarTituloSemCodigo((String) item.get("titulo_acao")))
                  .append(" | ").append(item.get("departamento"))
                  .append(" | ").append(formatarPercentualEnxuto(item.get("percentual"))).append("%")
                  .append(" |\n");
            }
        }
        return sb.toString();
    }

    @Tool("Busca as ações com MENOR execução do PAT (ano corrente) de um departamento específico, até 15 ações. Use para perguntas pontuais tipo 'quais ações estão mais atrasadas na PROEST'. Para pedidos de RELATÓRIO ou PANORAMA completo de uma unidade, use buscarDetalhamentoDesempenhoDepartamento em vez desta.")
    public String buscarExecucaoPATPorDepartamento(@P("nome ou parte do nome do departamento") String nomeDepartamento) {
        statusExecucao.definir("Consultando o PAT de " + nomeDepartamento + "...");
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
        statusExecucao.definir("Montando o panorama de " + nomeDepartamento + "...");
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
            SELECT p.codigo_acao, p.titulo_acao, ROUND(p.percentual_execucao * 100, 2) AS percentual_pat,
                   (SELECT ROUND(AVG(g.percentual_execucao) * 100, 2) FROM pat_execucao_departamento g WHERE g.codigo_acao = p.codigo_acao) AS pct_geral,
                   (SELECT COUNT(*) FROM pat_execucao_departamento g WHERE g.codigo_acao = p.codigo_acao) AS qtd_unidades
            FROM pat_execucao_departamento p
            WHERE p.departamento ILIKE ?
            ORDER BY p.percentual_execucao ASC
            LIMIT 8
            """;
        String sqlMelhores = """
            SELECT p.codigo_acao, p.titulo_acao, ROUND(p.percentual_execucao * 100, 2) AS percentual_pat,
                   (SELECT ROUND(AVG(g.percentual_execucao) * 100, 2) FROM pat_execucao_departamento g WHERE g.codigo_acao = p.codigo_acao) AS pct_geral,
                   (SELECT COUNT(*) FROM pat_execucao_departamento g WHERE g.codigo_acao = p.codigo_acao) AS qtd_unidades
            FROM pat_execucao_departamento p
            WHERE p.departamento ILIKE ?
            ORDER BY p.percentual_execucao DESC
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

        String posicao = posicaoNoRankingDeUgs(nomeDepartamento.trim());
        if (posicao != null) {
            sb.append(posicao).append("\n\n");
        }

        sb.append("AÇÕES COM MENOR EXECUÇÃO (até 8):\n");
        for (Map<String, Object> item : piores) {
            sb.append("- [").append(formatarCodigo(item.get("codigo_acao"))).append("] ").append(truncarTituloSemCodigo((String) item.get("titulo_acao"))).append(" — ").append(formatarPercentualEnxuto(item.get("percentual_pat"))).append("%").append(sufixoGeralDaAcao(item)).append("\n");
        }

        sb.append("\nAÇÕES COM MAIOR EXECUÇÃO (até 8):\n");
        for (Map<String, Object> item : melhores) {
            sb.append("- [").append(formatarCodigo(item.get("codigo_acao"))).append("] ").append(truncarTituloSemCodigo((String) item.get("titulo_acao"))).append(" — ").append(formatarPercentualEnxuto(item.get("percentual_pat"))).append("%").append(sufixoGeralDaAcao(item)).append("\n");
        }

        return sb.toString();
    }

    // Ação de um setor só não tem percentual geral: o dela é o da própria unidade.
    private String sufixoGeralDaAcao(Map<String, Object> item) {
        Object qtd = item.get("qtd_unidades");
        if (qtd == null || ((Number) qtd).intValue() < 2) {
            return " (ação de um setor só, sem percentual geral)";
        }
        return " (ação compartilhada: " + formatarPercentualEnxuto(item.get("pct_geral")) + "% geral entre " + qtd + " unidades)";
    }

    @Tool("Monta o RESUMO EXECUTIVO do PAT (ano corrente) da instituição inteira, com os mesmos números do painel de ranking: total de ações únicas e quantas estão concluídas, em andamento e paradas, média das UGs, UGs abaixo de 60%, 3 melhores e 3 piores UGs com posição, e ações compartilhadas paradas em 0% em todas as unidades. Use quando o usuário pedir 'resumo executivo', 'situação geral do PAT' ou 'visão geral das unidades'. Cite os números exatamente como vierem, sem recalcular.")
    public String resumoExecutivoPAT() {
        statusExecucao.definir("Montando o resumo executivo do PAT...");
        System.out.println(">>> TOOL CHAMADA: resumoExecutivoPAT()");

        // Mesma regra do KPI do painel: ação compartilhada conta uma vez e só está concluída se todas as unidades chegaram a 100%.
        Map<String, Object> acoes = jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE menor >= 1) AS concluidas,
                   COUNT(*) FILTER (WHERE maior = 0) AS paradas
            FROM (SELECT codigo_acao,
                         MIN(COALESCE(percentual_execucao, 0)) AS menor,
                         MAX(COALESCE(percentual_execucao, 0)) AS maior
                  FROM pat_execucao_departamento
                  WHERE codigo_acao IS NOT NULL
                  GROUP BY codigo_acao) a
            """);
        int totalAcoes = ((Number) acoes.get("total")).intValue();
        if (totalAcoes == 0) {
            return "Nenhuma ação de PAT encontrada no ano corrente.";
        }
        int concluidas = ((Number) acoes.get("concluidas")).intValue();
        int paradas = ((Number) acoes.get("paradas")).intValue();
        int emAndamento = totalAcoes - concluidas - paradas;

        List<Map<String, Object>> ugs = jdbcTemplate.queryForList("""
            SELECT departamento, ROUND(COALESCE(AVG(percentual_execucao), 0) * 100, 2) AS media
            FROM pat_execucao_departamento
            WHERE tipo_unidade = 'UG'
            GROUP BY departamento
            ORDER BY media DESC, departamento
            """);
        double somaMedias = 0;
        int abaixoDe60 = 0;
        for (Map<String, Object> ug : ugs) {
            double media = ((Number) ug.get("media")).doubleValue();
            somaMedias += media;
            if (media < 60) abaixoDe60++;
        }
        double mediaUgs = ugs.isEmpty() ? 0 : Math.round(somaMedias / ugs.size() * 10.0) / 10.0;

        StringBuilder sb = new StringBuilder();
        try {
            String atualizadoEm = jdbcTemplate.queryForObject("SELECT to_char(MAX(atualizado_em), 'DD/MM/YYYY HH24:MI') FROM pat_dados", String.class);
            if (atualizadoEm != null) sb.append("DADOS ATUALIZADOS EM: ").append(atualizadoEm).append("\n\n");
        } catch (Exception ignorada) {
            // sem data de atualização o resumo continua válido
        }

        sb.append("SITUAÇÃO GERAL: ").append(totalAcoes).append(" ações únicas no PAT (ação compartilhada conta uma vez). ")
          .append(concluidas).append(" concluídas (todas as unidades responsáveis em 100%), ")
          .append(emAndamento).append(" em andamento e ")
          .append(paradas).append(" paradas (0% em todas as unidades responsáveis).\n");
        sb.append("UGs: ").append(ugs.size()).append(" no ranking, média de execução de ").append(formatarPercentualEnxuto(mediaUgs))
          .append("%, ").append(abaixoDe60).append(" abaixo de 60% (faixa de atenção do painel).\n\n");

        if (!ugs.isEmpty()) {
            sb.append("MELHORES UGs (posição de ").append(ugs.size()).append("):\n").append(linhasDoResumo(ugs, true));
            sb.append("\nPIORES UGs (posição de ").append(ugs.size()).append("):\n").append(linhasDoResumo(ugs, false));
        }

        List<Map<String, Object>> compartilhadasParadas = jdbcTemplate.queryForList("""
            SELECT codigo_acao, MIN(titulo_acao) AS titulo, COUNT(DISTINCT departamento) AS qtd_unidades,
                   COUNT(*) OVER () AS total_grupos
            FROM pat_execucao_departamento
            WHERE codigo_acao IS NOT NULL
            GROUP BY codigo_acao
            HAVING COUNT(DISTINCT departamento) >= 2 AND MAX(COALESCE(percentual_execucao, 0)) = 0
            ORDER BY qtd_unidades DESC, codigo_acao
            LIMIT 5
            """);
        sb.append("\nAÇÕES COMPARTILHADAS PARADAS EM TODAS AS UNIDADES: ");
        if (compartilhadasParadas.isEmpty()) {
            sb.append("nenhuma.\n");
        } else {
            sb.append(compartilhadasParadas.get(0).get("total_grupos")).append(" no total; as 5 com mais unidades envolvidas:\n");
            for (Map<String, Object> a : compartilhadasParadas) {
                sb.append("- [").append(formatarCodigo(a.get("codigo_acao"))).append("] ")
                  .append(truncarTituloSemCodigo((String) a.get("titulo")))
                  .append(" — ").append(a.get("qtd_unidades")).append(" unidades\n");
            }
        }
        return sb.toString();
    }

    // Empatadas no mesmo percentual saem como um grupo: a ordem entre elas é só alfabética e não deve virar posição individual.
    private String linhasDoResumo(List<Map<String, Object>> ugs, boolean melhores) {
        int total = ugs.size();
        int passo = melhores ? 1 : -1;
        StringBuilder sb = new StringBuilder();
        int mostradas = 0;
        int i = melhores ? 0 : total - 1;
        while (mostradas < 3 && i >= 0 && i < total) {
            Object media = ugs.get(i).get("media");
            List<String> nomes = new java.util.ArrayList<>();
            int j = i;
            while (j >= 0 && j < total && ugs.get(j).get("media").equals(media)) {
                nomes.add((String) ugs.get(j).get("departamento"));
                j += passo;
            }
            int primeiraPosicao = melhores ? i + 1 : j + 2;
            int ultimaPosicao = melhores ? j : i + 1;
            if (nomes.size() == 1) {
                sb.append("- ").append(primeiraPosicao).append("ª: ").append(nomes.get(0))
                  .append(" — ").append(formatarPercentualEnxuto(media)).append("%\n");
            } else {
                int exibidos = Math.min(nomes.size(), 10);
                sb.append("- ").append(nomes.size()).append(" UGs empatadas em ").append(formatarPercentualEnxuto(media))
                  .append("% (posições ").append(primeiraPosicao).append(" a ").append(ultimaPosicao).append(" de ").append(total).append("): ")
                  .append(String.join("; ", nomes.subList(0, exibidos)));
                if (nomes.size() > exibidos) sb.append("; e mais ").append(nomes.size() - exibidos);
                sb.append("\n");
            }
            mostradas += nomes.size();
            i = j;
        }
        return sb.toString();
    }

    @Tool("Compara o PAT (ano corrente) de DUAS unidades/departamentos lado a lado: média de execução, posição no ranking de UGs, total de ações e quantas estão concluídas, em andamento e zeradas, mais as ações que as duas dividem com o percentual de cada uma. Use quando o usuário pedir para comparar, contrapor ou colocar duas unidades frente a frente. Passe o nome completo ou parte do nome de cada unidade. Cite os números exatamente como vierem.")
    public String compararUnidadesPAT(
            @P("nome ou parte do nome da primeira unidade") String unidadeA,
            @P("nome ou parte do nome da segunda unidade") String unidadeB) {
        statusExecucao.definir("Comparando as duas unidades no PAT...");
        System.out.println(">>> TOOL CHAMADA: compararUnidadesPAT(a=" + unidadeA + ", b=" + unidadeB + ")");

        List<String> candidatasA = buscarNomesDeDepartamento(unidadeA);
        List<String> candidatasB = buscarNomesDeDepartamento(unidadeB);
        String problema = problemaDeResolucao(unidadeA, candidatasA);
        if (problema == null) {
            problema = problemaDeResolucao(unidadeB, candidatasB);
        }
        if (problema != null) {
            return problema;
        }
        String nomeA = escolherDepartamento(unidadeA, candidatasA);
        String nomeB = escolherDepartamento(unidadeB, candidatasB);
        if (nomeA.equals(nomeB)) {
            return "As duas unidades são a mesma (" + nomeA + "). Peça ao usuário duas unidades diferentes.";
        }

        Map<String, Object> a = estatisticasDaUnidade(nomeA);
        Map<String, Object> b = estatisticasDaUnidade(nomeB);
        double mediaA = ((Number) a.get("media")).doubleValue();
        double mediaB = ((Number) b.get("media")).doubleValue();

        StringBuilder sb = new StringBuilder("COMPARAÇÃO NO PAT (ano corrente):\n\n");
        sb.append(blocoDaUnidade(nomeA, a)).append("\n").append(blocoDaUnidade(nomeB, b));
        sb.append("\nDIFERENÇA DE MÉDIA: ").append(formatarPercentualEnxuto(Math.abs(mediaA - mediaB))).append(" pontos percentuais a favor de ")
          .append(mediaA >= mediaB ? nomeA : nomeB).append(".\n");

        List<Map<String, Object>> comuns = jdbcTemplate.queryForList("""
            SELECT a.codigo_acao, a.titulo_acao,
                   ROUND(COALESCE(a.percentual_execucao, 0) * 100, 2) AS pct_a,
                   ROUND(COALESCE(b.percentual_execucao, 0) * 100, 2) AS pct_b,
                   COUNT(*) OVER () AS total_comuns
            FROM pat_execucao_departamento a
            JOIN pat_execucao_departamento b ON a.codigo_acao = b.codigo_acao
            WHERE a.departamento = ? AND b.departamento = ? AND a.codigo_acao IS NOT NULL
            ORDER BY ABS(COALESCE(a.percentual_execucao, 0) - COALESCE(b.percentual_execucao, 0)) DESC, a.codigo_acao
            LIMIT 8
            """, nomeA, nomeB);
        sb.append("\nAÇÕES EM COMUM: ");
        if (comuns.isEmpty()) {
            sb.append("as duas unidades não dividem nenhuma ação.\n");
        } else {
            sb.append(comuns.get(0).get("total_comuns")).append(" no total; as de maior diferença entre as duas (até 8):\n");
            for (Map<String, Object> acao : comuns) {
                sb.append("- [").append(formatarCodigo(acao.get("codigo_acao"))).append("] ")
                  .append(truncarTituloSemCodigo((String) acao.get("titulo_acao")))
                  .append(" — ").append(nomeA).append(": ").append(formatarPercentualEnxuto(acao.get("pct_a"))).append("% | ")
                  .append(nomeB).append(": ").append(formatarPercentualEnxuto(acao.get("pct_b"))).append("%\n");
            }
        }
        return sb.toString();
    }

    private List<String> buscarNomesDeDepartamento(String trecho) {
        if (trecho == null || trecho.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT departamento FROM pat_execucao_departamento WHERE departamento ILIKE ? ORDER BY departamento",
                String.class, "%" + trecho.trim() + "%");
    }

    private String escolherDepartamento(String trecho, List<String> candidatas) {
        return candidatas.stream().filter(n -> n.equalsIgnoreCase(trecho.trim())).findFirst().orElse(candidatas.get(0));
    }

    private String problemaDeResolucao(String trecho, List<String> candidatas) {
        if (candidatas.isEmpty()) {
            return "Nenhuma unidade encontrada no PAT com o nome '" + trecho + "'. Peça ao usuário para conferir o nome.";
        }
        boolean exata = candidatas.stream().anyMatch(n -> n.equalsIgnoreCase(trecho.trim()));
        if (candidatas.size() > 1 && !exata) {
            return "O nome '" + trecho + "' corresponde a várias unidades: " + String.join("; ", candidatas.subList(0, Math.min(candidatas.size(), 8)))
                    + ". Peça ao usuário para dizer qual delas.";
        }
        return null;
    }

    private Map<String, Object> estatisticasDaUnidade(String nomeExato) {
        return jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS total,
                   ROUND(COALESCE(AVG(COALESCE(percentual_execucao, 0)), 0) * 100, 2) AS media,
                   COUNT(*) FILTER (WHERE COALESCE(percentual_execucao, 0) = 0) AS zeradas,
                   COUNT(*) FILTER (WHERE COALESCE(percentual_execucao, 0) > 0 AND percentual_execucao < 1) AS em_andamento,
                   COUNT(*) FILTER (WHERE percentual_execucao >= 1) AS concluidas
            FROM pat_execucao_departamento
            WHERE departamento = ?
            """, nomeExato);
    }

    private String blocoDaUnidade(String nome, Map<String, Object> e) {
        String posicao = posicaoNoRankingDeUgs(nome);
        return "- " + nome + ": média de " + formatarPercentualEnxuto(e.get("media")) + "%, "
                + e.get("total") + " ações (" + e.get("concluidas") + " concluídas, " + e.get("em_andamento") + " em andamento, " + e.get("zeradas") + " zeradas). "
                + (posicao != null ? posicao.replace(" Cite exatamente esta posição; não calcule outra.", "") : "Fora do ranking de UGs.") + "\n";
    }

    @Tool("Lista as UGs com mais ações SEM NENHUMA EXECUÇÃO (0%) no PAT (ano corrente), com o total de ações de cada uma, quantas estão zeradas, o percentual de zeradas e quantas dessas zeradas são ações compartilhadas com outras unidades. Use para perguntas como 'quais unidades têm mais ações paradas/zeradas/sem execução'. Cite os números exatamente como vierem.")
    public String acoesSemExecucaoPorUG(
            @P(value = "quantas UGs listar, padrão 10", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 50);
        statusExecucao.definir("Levantando as ações sem execução por UG...");
        System.out.println(">>> TOOL CHAMADA: acoesSemExecucaoPorUG(limite=" + qtd + ")");

        // Mesma regra do restante do PAT: ação zerada é percentual_execucao nulo ou 0 na atribuição daquela unidade.
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
            SELECT p.departamento,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE COALESCE(p.percentual_execucao, 0) = 0) AS zeradas,
                   COUNT(*) FILTER (WHERE COALESCE(p.percentual_execucao, 0) = 0 AND c.qtd_unidades > 1) AS zeradas_compartilhadas,
                   COUNT(*) OVER () AS ugs_com_zeradas,
                   SUM(COUNT(*) FILTER (WHERE COALESCE(p.percentual_execucao, 0) = 0)) OVER () AS zeradas_total
            FROM pat_execucao_departamento p
            JOIN (SELECT codigo_acao, COUNT(DISTINCT departamento) AS qtd_unidades
                  FROM pat_execucao_departamento WHERE codigo_acao IS NOT NULL GROUP BY codigo_acao) c
              ON c.codigo_acao = p.codigo_acao
            WHERE p.tipo_unidade = 'UG'
            GROUP BY p.departamento
            HAVING COUNT(*) FILTER (WHERE COALESCE(p.percentual_execucao, 0) = 0) > 0
            ORDER BY zeradas DESC, p.departamento
            LIMIT %d
            """.formatted(qtd));
        if (linhas.isEmpty()) {
            return "Nenhuma UG tem ação sem execução no PAT do ano corrente.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(linhas.get(0).get("ugs_com_zeradas")).append(" UGs têm pelo menos uma ação sem execução, somando ")
          .append(linhas.get(0).get("zeradas_total")).append(" atribuições zeradas (uma ação compartilhada conta uma vez para cada UG). As ")
          .append(linhas.size()).append(" com mais ações zeradas:\n\n");
        sb.append("| UG | Ações | Zeradas | % zeradas | Zeradas compartilhadas |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Map<String, Object> l : linhas) {
            double total = ((Number) l.get("total")).doubleValue();
            double zeradas = ((Number) l.get("zeradas")).doubleValue();
            sb.append("| ").append(l.get("departamento"))
              .append(" | ").append(l.get("total"))
              .append(" | ").append(l.get("zeradas"))
              .append(" | ").append(formatarPercentualEnxuto(Math.round(zeradas / total * 1000.0) / 10.0)).append("%")
              .append(" | ").append(l.get("zeradas_compartilhadas"))
              .append(" |\n");
        }
        return sb.toString();
    }

    // Mesma regra do painel de ranking (só UG, desempate pelo nome): evita o agente contar coordenações e divergir da tela.
    private String posicaoNoRankingDeUgs(String nomeDepartamento) {
        String sql = """
            WITH r AS (
                SELECT departamento,
                       ROUND(COALESCE(AVG(percentual_execucao), 0) * 100, 2) AS media,
                       ROW_NUMBER() OVER (ORDER BY ROUND(COALESCE(AVG(percentual_execucao), 0) * 100, 2) DESC, departamento) AS pos,
                       COUNT(*) OVER () AS total
                FROM pat_execucao_departamento
                WHERE tipo_unidade = 'UG'
                GROUP BY departamento
            )
            SELECT pos, total, (SELECT COUNT(*) FROM r r2 WHERE r2.media = r.media) AS com_mesma_media
            FROM r WHERE departamento ILIKE ? ORDER BY pos LIMIT 1
            """;
        try {
            List<Map<String, Object>> linhas = jdbcTemplate.queryForList(sql, "%" + nomeDepartamento + "%");
            if (linhas.isEmpty()) {
                return null;
            }
            Map<String, Object> linha = linhas.get(0);
            int empatadas = ((Number) linha.get("com_mesma_media")).intValue() - 1;
            return "POSIÇÃO NO RANKING DE UGs (mesmo ranking do painel, sem coordenações): " + linha.get("pos") + "ª de " + linha.get("total")
                    + (empatadas > 0 ? " (empatada em percentual com mais " + empatadas + " UG(s))" : " (sem empate)")
                    + ". Cite exatamente esta posição; não calcule outra.";
        } catch (Exception e) {
            return null;
        }
    }

    // titulo_acao vem do dado bruto e traz o nome completo do departamento colado após " | " —
    // redundante aqui porque as duas listas já são de um único departamento (o parâmetro da busca),
    // e repetir esse texto 16x infla desnecessariamente o tamanho da resposta (relevante pro TPM do Groq).
    private String truncarTitulo(String tituloAcao) {
        if (tituloAcao == null) return "";
        int separador = tituloAcao.indexOf(" | ");
        return separador >= 0 ? tituloAcao.substring(0, separador) : tituloAcao;
    }

    // Igual truncarTitulo, mas também tira o código colado no início do título (ex: "U 3.1.2.13 -
    // Desenvolver o..."). Só usar em lugares que já mostram o código separado (ex: "[U 3.1.2.13] ...")
    // — senão o código aparece duas vezes na mesma linha.
    private String truncarTituloSemCodigo(String tituloAcao) {
        String semDepartamento = truncarTitulo(tituloAcao);
        return semDepartamento.replaceFirst("^[A-Za-zÀ-ÿ]+ [0-9]+(?:\\.[0-9]+)*\\s*-\\s*", "");
    }

    // @Tool("Conta quantas ações do PDI cada departamento/UG é responsável, ranqueando por quantidade. Use para perguntas tipo 'qual UG tem menos/mais ações no PDI'. Uma ação pode ter vários departamentos responsáveis; esta ferramenta conta corretamente cada departamento separadamente. Permite filtrar só por UG ou só por UA, usando a classificação já conhecida do PAT.")
    public String contarAcoesPorDepartamentoPDI(
            @P("'menos' para ranquear do menor para o maior número de ações, 'mais' para o maior primeiro") String ordem,
            @P(value = "'UG' para filtrar só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade de departamentos a retornar, padrão 10. Se o usuário pedir 'todos os departamentos' ou não quiser recorte nenhum, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        statusExecucao.definir("Contando ações do PDI por departamento...");
        System.out.println(">>> TOOL CHAMADA: contarAcoesPorDepartamentoPDI(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        // Texto SOMENTE em text block (""") até aqui — um text block corta espaço em branco no
        // fim de cada linha automaticamente (whitespace incidental), então concatenar variável
        // logo após um " """ na mesma linha gruda a palavra sem espaço nenhum na SQL final
        // (virava "qtd_acoesDESCLIMIT ?", erro de sintaxe). O trecho ORDER BY/LIMIT sai do
        // text block e usa string normal, com o espaço explícito, igual contarAcoesPorDepartamentoPAT.
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
            """
            + (filtrarTipo ? "WHERE m.tipo_unidade = ? " : "")
            + "ORDER BY p.qtd_acoes " + direcao + " "
            + "LIMIT ?";

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
        statusExecucao.definir("Contando ações do PAT por departamento...");
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
        // Coluna "Tipo" só agrega informação quando a lista mistura UA e UG — filtrado por um
        // tipo só, toda linha repetiria o mesmo valor, redundante.
        if (filtrarTipo) {
            sb.append("| Departamento | Qtd Ações |\n");
            sb.append("|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(item.get("departamento"))
                  .append(" | ").append(item.get("qtd_acoes"))
                  .append(" |\n");
            }
        } else {
            sb.append("| Departamento | Tipo | Qtd Ações |\n");
            sb.append("|---|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(item.get("departamento"))
                  .append(" | ").append(item.get("tipo_unidade"))
                  .append(" | ").append(item.get("qtd_acoes"))
                  .append(" |\n");
            }
        }
        return sb.toString();
    }

    @Tool("Conta o número de ações ÚNICAS do PAT (ano corrente) — cada ação contada uma vez só, mesmo quando compartilhada entre vários departamentos. Use para perguntas tipo 'quantas ações tem o PAT no total', 'quantas ações únicas existem' ou 'qual o total de ações do plano'. Diferente de contarAcoesPorDepartamentoPAT, que soma atribuições por departamento e por isso conta uma ação compartilhada mais de uma vez.")
    public String contarAcoesUnicasPAT() {
        statusExecucao.definir("Contando o total de ações únicas do PAT...");
        System.out.println(">>> TOOL CHAMADA: contarAcoesUnicasPAT()");
        String sql = "SELECT COUNT(DISTINCT codigo_acao) FROM pat_execucao_departamento WHERE codigo_acao IS NOT NULL";
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class);
        String sqlAtribuicoes = "SELECT COUNT(*) FROM pat_execucao_departamento WHERE codigo_acao IS NOT NULL";
        Integer atribuicoes = jdbcTemplate.queryForObject(sqlAtribuicoes, Integer.class);

        // Uma ação compartilhada entre UA e UG (raro, mas possível) conta nos dois grupos —
        // por isso ug+ua pode não bater exatamente com "total" (o total de códigos distintos).
        String sqlPorTipo = """
            SELECT tipo_unidade, COUNT(DISTINCT codigo_acao) AS qtd
            FROM pat_execucao_departamento
            WHERE codigo_acao IS NOT NULL AND tipo_unidade IS NOT NULL
            GROUP BY tipo_unidade
            """;
        Map<String, Integer> qtdPorTipo = new HashMap<>();
        for (Map<String, Object> linha : jdbcTemplate.queryForList(sqlPorTipo)) {
            qtdPorTipo.put((String) linha.get("tipo_unidade"), ((Number) linha.get("qtd")).intValue());
        }

        System.out.println(">>> TOOL RESULTADO: total=" + total + ", atribuicoes=" + atribuicoes + ", porTipo=" + qtdPorTipo);

        return "O PAT (ano corrente) tem " + total + " ações únicas no total. "
             + "Isso vem de " + atribuicoes + " atribuições de departamento no total — a diferença "
             + "entre os dois números é porque algumas ações são compartilhadas por mais de um departamento.\n\n"
             + "Por tipo de unidade (uma ação compartilhada entre UA e UG conta nos dois grupos): "
             + qtdPorTipo.getOrDefault("UA", 0) + " ação(ões) com UA responsável, "
             + qtdPorTipo.getOrDefault("UG", 0) + " ação(ões) com UG responsável.";
    }

    // @Tool("Compara a execução de uma mesma ação entre o PDI (acumulado dos 5 anos) e o PAT (ano corrente), usando o código da ação. Use quando o usuário quiser entender se uma ação está adiantada ou atrasada em relação ao plano de longo prazo.")
    // Desativada junto com as outras 6 ferramentas de PDI acima — mesma razão (ver comentário lá).
    public String compararExecucaoPDIxPAT(@P("código exato da ação, ex: 1.1.1.3") String codigo) {
        statusExecucao.definir("Comparando PAT e PDI da ação " + codigo + "...");
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
        statusExecucao.definir("Investigando a ação compartilhada " + codigoAcao + "...");
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
        String tituloAcao = truncarTituloSemCodigo((String) resultado.get(0).get("titulo_acao"));

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