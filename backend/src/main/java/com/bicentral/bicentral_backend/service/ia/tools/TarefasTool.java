package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TarefasTool {

    // LIMIAR_MOSTRAR_TUDO evita truncar um total próximo do padrão (ex: 13 em 10) — só amostra de verdade quando o total é bem maior.
    private static final int LIMITE_PADRAO = 10;
    private static final int LIMIAR_MOSTRAR_TUDO = 15;

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;
    private final StatusExecucaoAgente statusExecucao;

    public TarefasTool(JdbcTemplate jdbcTemplate, UsuarioService usuarioService, StatusExecucaoAgente statusExecucao) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
        this.statusExecucao = statusExecucao;
    }

    @Tool("Busca as tarefas do PAT sob responsabilidade do usuário atualmente logado no chat. Use quando o usuário perguntar 'minhas tarefas', 'o que eu tenho pra fazer', 'como estão minhas pendências', ou pedir um panorama pessoal do próprio trabalho.")
    public String buscarMinhasTarefas(
            @P(value = "quantidade máxima de tarefas a retornar, padrão 10 se o usuário não especificar. Se o usuário pedir 'todas', 'sem limite' ou 'lista tudo', passe 500. Se pedir uma quantidade específica, passe esse número exato — nunca mostre menos do que foi pedido.", required = false) Integer limite) {
        boolean limiteExplicito = limite != null && limite > 0;
        int qtd = limiteExplicito ? Math.min(limite, 500) : LIMITE_PADRAO;
        statusExecucao.definir("Buscando suas tarefas...");
        System.out.println(">>> TOOL CHAMADA: buscarMinhasTarefas(limite=" + qtd + ")");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        String nomeResponsavel;
        try {
            nomeResponsavel = jdbcTemplate.queryForObject(
                "SELECT nome_responsavel FROM usuario_responsavel WHERE usuario_id = ?",
                String.class, usuario.getId()
            );
        } catch (Exception e) {
            return "O usuário atual ainda não tem um nome de responsável vinculado no sistema. " +
                   "Um administrador precisa cadastrar esse vínculo no painel admin para essa consulta funcionar.";
        }

        Integer totalTarefas = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pat_tarefas WHERE dados_completos->>'Responsável' ILIKE ?",
            Integer.class, "%" + nomeResponsavel.trim() + "%");

        if (!limiteExplicito && totalTarefas != null && totalTarefas <= LIMIAR_MOSTRAR_TUDO) {
            qtd = totalTarefas;
        }

        List<Map<String, Object>> tarefas = jdbcTemplate.queryForList("""
            SELECT
                dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                departamento,
                NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual,
                dados_completos->>'Data Inicial' AS data_inicial,
                to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final,
                (to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                    AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100) AS atrasada,
                (CURRENT_DATE - to_date(dados_completos->>'Data Final', 'DD/MM/YYYY')) AS dias_atraso
            FROM pat_tarefas
            WHERE dados_completos->>'Responsável' ILIKE ?
            ORDER BY atrasada DESC, to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') ASC NULLS LAST
            LIMIT ?
            """, "%" + nomeResponsavel.trim() + "%", qtd);

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s) de " + totalTarefas + " para " + nomeResponsavel);

        if (tarefas.isEmpty()) {
            return "Nenhuma tarefa encontrada para " + nomeResponsavel + " no PAT atual.";
        }

        // Título da ação não vem de pat_tarefas — busca em lote (1 query, não 1 por linha).
        List<String> codigos = tarefas.stream()
            .map(t -> (String) t.get("codigo_acao"))
            .filter(c -> c != null && !c.isBlank())
            .distinct().toList();
        Map<String, String> tituloPorCodigo = new java.util.HashMap<>();
        if (!codigos.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(codigos.size(), "?"));
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                    "SELECT DISTINCT codigo_acao, titulo_acao FROM pat_execucao_departamento WHERE codigo_acao IN (" + placeholders + ")",
                    codigos.toArray())) {
                tituloPorCodigo.put((String) row.get("codigo_acao"), (String) row.get("titulo_acao"));
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean truncado = totalTarefas != null && totalTarefas > tarefas.size();
        sb.append("Tarefas de ").append(nomeResponsavel).append(" (")
          .append(truncado ? tarefas.size() + " mais urgentes de " + totalTarefas + " no total" : tarefas.size() + " no total")
          .append("):\n\n");
        sb.append("| Ação | Título da Ação | Tarefa | % | Atraso | Prazo |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            boolean atrasada = Boolean.TRUE.equals(t.get("atrasada"));
            String atraso = atrasada ? "⚠️ " + t.get("dias_atraso") + "d" : "—";
            String codigo = (String) t.get("codigo_acao");

            sb.append("| ").append(formatarCodigo(codigo))
              .append(" | ").append(temaSemCodigo(tituloPorCodigo.get(codigo), codigo))
              .append(" | ").append(t.get("titulo_tarefa"))
              .append(" | ").append(formatarPercentualEnxuto(t.get("percentual"))).append("%")
              .append(" | ").append(atraso)
              .append(" | ").append(formatarData(t.get("data_final")))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Busca tarefas do PAT (ano corrente) com filtros combináveis: departamento, código de ação, palavra-chave no TÍTULO/NOME da ação (quando o usuário não sabe o código, ex: 'a ação de monitorar os indicadores'), responsável, palavra-chave no título da tarefa e status (atrasada/concluída/em andamento). Combine quantos filtros fizerem sentido pra pergunta — ex: tarefas concluídas de uma ação num departamento específico. Sempre informa o total real de tarefas encontradas, mesmo quando mostra só uma amostra por causa do limite. Use pra qualquer LISTAGEM de tarefas (mesmo quando o pedido menciona 'ação' — ex: 'tarefas dessa ação', 'as tarefas concluídas dela no meu setor' — desde que o pedido seja listar/mostrar tarefas, não contar/agregar) que não seja sobre o próprio usuário logado (aí use buscarMinhasTarefas), nem uma CONTAGEM agregada por departamento/UG (aí use contarTarefasPorDepartamento), nem por ação (aí use contarTarefasPorAcao — essa NUNCA lista tarefa individual, só soma quantidade).")
    public String buscarTarefas(
            @P(value = "nome ou parte do nome do departamento, opcional", required = false) String departamento,
            @P(value = "código exato da ação, com ou sem a letra na frente (ex: 'U 3.1.2.35' ou só '3.1.2.35') — use quando o usuário souber o código, opcional", required = false) String codigoAcao,
            @P(value = "palavra-chave a buscar no TÍTULO/NOME da ação (não da tarefa) — use quando o usuário descrever a ação pelo nome/assunto em vez do código (ex: 'a ação de monitorar os indicadores', 'ação de acessibilidade'), opcional", required = false) String tituloAcao,
            @P(value = "nome ou parte do nome do responsável pela tarefa, opcional", required = false) String responsavel,
            @P(value = "palavra-chave a buscar no título da tarefa (não da ação), opcional", required = false) String palavraChave,
            @P(value = "'atrasada' (prazo vencido e não concluída), 'concluida' ou 'em_andamento' (dentro do prazo, não concluída); deixe null para qualquer status", required = false) String status,
            @P(value = "'prazo' (mais urgentes primeiro, padrão), 'percentual' (menor conclusão primeiro) ou 'acao'/'codigo' (ordem numérica do código da ação, ex: 3.1, 4, 5.1 — use quando o usuário pedir explicitamente pra ordenar pelo número/código da ação, ex: 'ordena pelo número da ação', 'na ordem do PDI')", required = false) String ordenarPor,
            @P(value = "quantidade máxima de tarefas a retornar, padrão 10 se o usuário não especificar. Se o usuário pedir 'todas', 'sem limite' ou 'lista tudo', passe 500. Se pedir uma quantidade específica (ex: 'as 30 mais urgentes'), passe esse número exato — nunca mostre menos do que foi pedido.", required = false) Integer limite) {

        boolean limiteExplicito = limite != null && limite > 0;
        int qtd = limiteExplicito ? Math.min(limite, 500) : LIMITE_PADRAO;
        boolean ordenarPorPercentual = "percentual".equalsIgnoreCase(ordenarPor);
        boolean ordenarPorAcao = "acao".equalsIgnoreCase(ordenarPor) || "ação".equalsIgnoreCase(ordenarPor) || "codigo".equalsIgnoreCase(ordenarPor) || "código".equalsIgnoreCase(ordenarPor);
        statusExecucao.definir(montarStatusBusca(departamento, responsavel, status));
        System.out.println(">>> TOOL CHAMADA: buscarTarefas(departamento=" + departamento + ", codigoAcao=" + codigoAcao + ", tituloAcao=" + tituloAcao + ", responsavel=" + responsavel
            + ", palavraChave=" + palavraChave + ", status=" + status + ", ordenarPor=" + ordenarPor + ", limite=" + qtd + ")");

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (departamento != null && !departamento.isBlank()) {
            where.append(" AND b.departamento ILIKE ? ");
            params.add("%" + departamento.trim() + "%");
        }
        if (codigoAcao != null && !codigoAcao.isBlank()) {
            // Usuário raramente digita a letra que antecede o código (ex: "U 3.1.2.35") — compara
            // só a parte numérica, mesmo padrão de rastrearGargaloEmAcaoCompartilhada.
            where.append(" AND regexp_replace(b.codigo_acao, '^[A-Za-zÀ-ÿ]+\\s*', '') = regexp_replace(?, '^[A-Za-zÀ-ÿ]+\\s*', '') ");
            params.add(codigoAcao.trim());
        }
        if (tituloAcao != null && !tituloAcao.isBlank()) {
            where.append(" AND b.titulo_acao ILIKE ? ");
            params.add("%" + tituloAcao.trim() + "%");
        }
        if (responsavel != null && !responsavel.isBlank()) {
            where.append(" AND b.responsavel ILIKE ? ");
            params.add("%" + responsavel.trim() + "%");
        }
        if (palavraChave != null && !palavraChave.isBlank()) {
            where.append(" AND b.titulo_tarefa ILIKE ? ");
            params.add("%" + palavraChave.trim() + "%");
        }
        if ("atrasada".equalsIgnoreCase(status)) {
            where.append(" AND b.data_final < CURRENT_DATE AND COALESCE(b.percentual_tarefa, 0) < 100 ");
        } else if ("concluida".equalsIgnoreCase(status) || "concluída".equalsIgnoreCase(status)) {
            where.append(" AND COALESCE(b.percentual_tarefa, 0) >= 100 ");
        } else if ("em_andamento".equalsIgnoreCase(status)) {
            where.append(" AND COALESCE(b.percentual_tarefa, 0) < 100 AND (b.data_final IS NULL OR b.data_final >= CURRENT_DATE) ");
        }

        // JOIN com pat_execucao_departamento dentro do CTE, não só na query final — assim o filtro por tituloAcao também vale na query de COUNT.
        String baseSql = """
            WITH base AS (
                SELECT
                    t.departamento,
                    substring(t.dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                    t.dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    t.dados_completos->>'Responsável' AS responsavel,
                    NULLIF(regexp_replace(t.dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual_tarefa,
                    to_date(t.dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final,
                    e.titulo_acao,
                    e.percentual_execucao
                FROM pat_tarefas t
                LEFT JOIN pat_execucao_departamento e
                    ON e.codigo_acao = substring(t.dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*')
                    AND e.departamento = t.departamento
            )
            """;

        Integer total = jdbcTemplate.queryForObject(
            baseSql + "SELECT COUNT(*) FROM base b" + where, Integer.class, params.toArray());

        if (total == null || total == 0) {
            return "Nenhuma tarefa encontrada com esses filtros.";
        }

        if (!limiteExplicito && total <= LIMIAR_MOSTRAR_TUDO) {
            qtd = total;
        }

        // Ordem numérica de verdade (3.1, 4, 5.1...), não alfabética (que colocaria "10" antes de
        // "2") — quebra o código em pedaços pelo ponto e compara como array de inteiros.
        String ordem;
        if (ordenarPorAcao) {
            ordem = "string_to_array(regexp_replace(b.codigo_acao, '^[A-Za-zÀ-ÿ]+\\s*', ''), '.')::int[] ASC NULLS LAST";
        } else if (ordenarPorPercentual) {
            ordem = "b.percentual_tarefa ASC NULLS LAST";
        } else {
            ordem = "(b.data_final < CURRENT_DATE AND COALESCE(b.percentual_tarefa, 0) < 100) DESC, b.data_final ASC NULLS LAST";
        }

        String listaSql = baseSql + """
            SELECT
                b.codigo_acao, b.titulo_acao, b.titulo_tarefa, b.departamento, b.responsavel,
                ROUND(b.percentual_execucao * 100, 2) AS percentual_acao,
                b.percentual_tarefa, b.data_final,
                (b.data_final < CURRENT_DATE AND COALESCE(b.percentual_tarefa, 0) < 100) AS atrasada,
                (CURRENT_DATE - b.data_final) AS dias_atraso
            FROM base b
            """ + where + " ORDER BY " + ordem + " LIMIT ?";

        List<Object> listaParams = new ArrayList<>(params);
        listaParams.add(qtd);
        List<Map<String, Object>> tarefas = jdbcTemplate.queryForList(listaSql, listaParams.toArray());

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s) de " + total + " no total");

        StringBuilder sb = new StringBuilder();
        boolean truncado = total > tarefas.size();
        sb.append(truncado ? tarefas.size() + " tarefa(s) mostrada(s) de " + total + " no total" : total + " tarefa(s) encontrada(s)")
          .append(":\n\n");
        sb.append("| Ação | Título da Ação | Tarefa | Departamento | Responsável | % Ação | % Tarefa | Atraso | Prazo |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            boolean atrasada = Boolean.TRUE.equals(t.get("atrasada"));
            String atraso = atrasada ? "⚠️ " + t.get("dias_atraso") + "d" : "—";
            Object percentualAcao = t.get("percentual_acao");
            String codigo = formatarCodigo(t.get("codigo_acao"));
            sb.append("| ").append(codigo)
              .append(" | ").append(temaSemCodigo((String) t.get("titulo_acao"), (String) t.get("codigo_acao")))
              .append(" | ").append(t.get("titulo_tarefa"))
              .append(" | ").append(t.get("departamento"))
              .append(" | ").append(t.get("responsavel") != null ? t.get("responsavel") : "—")
              .append(" | ").append(percentualAcao == null ? "—" : formatarPercentualEnxuto(percentualAcao) + "%")
              .append(" | ").append(formatarPercentualEnxuto(t.get("percentual_tarefa"))).append("%")
              .append(" | ").append(atraso)
              .append(" | ").append(formatarData(t.get("data_final")))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Conta quantas tarefas do PAT (ano corrente) cada departamento/UG tem no total, ranqueando por quantidade. Use para perguntas tipo 'quantas tarefas tem cada UG', 'relaciona a quantidade de tarefas por unidade', 'qual departamento tem mais tarefas'. Cobre TODOS os departamentos de uma vez com o total real de cada um — diferente de buscarTarefas, que lista tarefas individuais (com ou sem filtro) mas não soma um total por departamento. Permite filtrar só por UG ou só por UA.")
    public String contarTarefasPorDepartamento(
            @P("'menos' para ranquear do menor para o maior número de tarefas, 'mais' para o maior primeiro") String ordem,
            @P(value = "'UG' para filtrar só Unidades Gestoras, 'UA' para só Unidades Acadêmicas, deixe null para todas", required = false) String tipoUnidade,
            @P(value = "quantidade de departamentos a retornar, padrão 10. Se o usuário pedir 'todos os departamentos'/'todas as UGs' ou não quiser recorte nenhum, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        boolean filtrarTipo = tipoUnidade != null && (tipoUnidade.equalsIgnoreCase("UA") || tipoUnidade.equalsIgnoreCase("UG"));
        statusExecucao.definir("Contando tarefas por departamento...");
        System.out.println(">>> TOOL CHAMADA: contarTarefasPorDepartamento(ordem=" + ordem + ", tipoUnidade=" + tipoUnidade + ", limite=" + qtd + ")");

        // Classificação UA/UG não existe em pat_tarefas — reaproveita o mapeamento já resolvido
        // na view pat_execucao_departamento (mesmo padrão de contarAcoesPorDepartamentoPDI).
        // Texto SOMENTE em text block (""") até aqui — um text block corta espaço em branco no
        // fim de cada linha automaticamente (whitespace incidental), então concatenar variável
        // logo após um " """ na mesma linha gruda a palavra sem espaço nenhum na SQL final
        // (virava "qtd_tarefasDESCLIMIT ?", erro de sintaxe). O trecho ORDER BY/LIMIT sai do
        // text block e usa string normal, com o espaço explícito, igual contarAcoesPorDepartamentoPAT.
        String sql = """
            WITH mapeamento AS (
                SELECT DISTINCT departamento, tipo_unidade FROM pat_execucao_departamento
            )
            SELECT t.departamento, m.tipo_unidade, COUNT(*) AS qtd_tarefas
            FROM pat_tarefas t
            LEFT JOIN mapeamento m ON m.departamento = t.departamento
            """
            + (filtrarTipo ? "WHERE m.tipo_unidade = ? " : "")
            + "GROUP BY t.departamento, m.tipo_unidade "
            + "ORDER BY qtd_tarefas " + direcao + " "
            + "LIMIT ?";

        List<Map<String, Object>> resultado = filtrarTipo
            ? jdbcTemplate.queryForList(sql, tipoUnidade.toUpperCase(), qtd)
            : jdbcTemplate.queryForList(sql, qtd);

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " departamento(s) retornado(s)");

        if (resultado.isEmpty()) {
            return "Nenhuma tarefa encontrada no PAT.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Contagem de tarefas do PAT por departamento")
          .append(filtrarTipo ? " (filtrado por " + tipoUnidade.toUpperCase() + ")" : ", tipo pode vir nulo se o departamento não aparecer no PAT")
          .append(":\n\n");
        // Coluna "Tipo" só agrega informação quando a lista mistura UA e UG — filtrado por um
        // tipo só, toda linha repetiria o mesmo valor (ex: "UG" 20 vezes seguidas), redundante.
        if (filtrarTipo) {
            sb.append("| Departamento | Qtd Tarefas |\n");
            sb.append("|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(item.get("departamento"))
                  .append(" | ").append(item.get("qtd_tarefas"))
                  .append(" |\n");
            }
        } else {
            sb.append("| Departamento | Tipo | Qtd Tarefas |\n");
            sb.append("|---|---|---|\n");
            for (Map<String, Object> item : resultado) {
                sb.append("| ").append(item.get("departamento"))
                  .append(" | ").append(item.get("tipo_unidade") != null ? item.get("tipo_unidade") : "—")
                  .append(" | ").append(item.get("qtd_tarefas"))
                  .append(" |\n");
            }
        }
        return sb.toString();
    }

    @Tool("Conta quantas tarefas do PAT (ano corrente) cada AÇÃO tem, ranqueando por quantidade — soma as tarefas de TODOS os departamentos que respondem por ela (uma ação compartilhada entre vários departamentos conta o total combinado). Use SÓ para perguntas de CONTAGEM/RANKING tipo 'qual ação tem mais tarefas', 'quais ações concentram mais tarefas'. NÃO use se o usuário quer VER/LISTAR as tarefas em si (mesmo que fale 'ação' e 'tarefa' juntos, ex: 'as tarefas concluídas dessa ação no meu setor') — aí é buscarTarefas com o parâmetro codigoAcao, nunca esta ferramenta (ela só devolve números agregados, nenhum título de tarefa individual).")
    public String contarTarefasPorAcao(
            @P("'menos' para ranquear do menor para o maior número de tarefas, 'mais' para o maior primeiro") String ordem,
            @P(value = "quantidade de ações a retornar, padrão 10. Se o usuário pedir 'todas as ações' ou não quiser recorte, passe 500", required = false) Integer limite) {
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        String direcao = ordem != null && ordem.toLowerCase().contains("mais") ? "DESC" : "ASC";
        statusExecucao.definir("Contando tarefas por ação...");
        System.out.println(">>> TOOL CHAMADA: contarTarefasPorAcao(ordem=" + ordem + ", limite=" + qtd + ")");

        // MIN() pega um título qualquer — é o mesmo pra todo departamento que compartilha a ação.
        String sql = """
            WITH contagem AS (
                SELECT substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                       COUNT(*) AS qtd_tarefas
                FROM pat_tarefas
                GROUP BY codigo_acao
            )
            SELECT c.codigo_acao, MIN(e.titulo_acao) AS titulo_acao, c.qtd_tarefas
            FROM contagem c
            LEFT JOIN pat_execucao_departamento e ON e.codigo_acao = c.codigo_acao
            WHERE c.codigo_acao IS NOT NULL
            GROUP BY c.codigo_acao, c.qtd_tarefas
            """
            + "ORDER BY c.qtd_tarefas " + direcao + " "
            + "LIMIT ?";

        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, qtd);

        System.out.println(">>> TOOL RESULTADO: " + resultado.size() + " ação(ões) retornada(s)");

        if (resultado.isEmpty()) {
            return "Nenhuma tarefa encontrada no PAT.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Contagem de tarefas do PAT por ação (soma de todos os departamentos que a compartilham):\n\n");
        sb.append("| Ação | Título da Ação | Qtd Tarefas |\n");
        sb.append("|---|---|---|\n");
        for (Map<String, Object> item : resultado) {
            sb.append("| ").append(formatarCodigo(item.get("codigo_acao")))
              .append(" | ").append(temaSemCodigo((String) item.get("titulo_acao"), (String) item.get("codigo_acao")))
              .append(" | ").append(item.get("qtd_tarefas"))
              .append(" |\n");
        }
        return sb.toString();
    }

    /** Tira o sufixo "| Departamento (TIPO)" e o prefixo do código — duplica truncarTituloSemCodigo (privado em ConsultaAcoesTool/RelatorioService). */
    private String temaSemCodigo(String tituloAcao, String codigo) {
        if (tituloAcao == null) return "—";
        int separador = tituloAcao.indexOf(" | ");
        String semDepartamento = separador >= 0 ? tituloAcao.substring(0, separador) : tituloAcao;
        if (codigo == null || codigo.isBlank()) return semDepartamento;
        return semDepartamento.replaceFirst("^" + java.util.regex.Pattern.quote(codigo) + "\\s*-\\s*", "");
    }

    /** Frase do status ao vivo pra buscarTarefas — como ela aceita vários filtros combináveis,
     * monta um resumo curto do que está sendo buscado em vez de um "Buscando tarefas..." fixo
     * que não diz nada, igual as outras ferramentas de departamento/código já fazem. */
    private String montarStatusBusca(String departamento, String responsavel, String status) {
        StringBuilder sb = new StringBuilder("Buscando tarefas");
        if ("atrasada".equalsIgnoreCase(status)) sb.append(" atrasadas");
        else if ("concluida".equalsIgnoreCase(status) || "concluída".equalsIgnoreCase(status)) sb.append(" concluídas");
        else if ("em_andamento".equalsIgnoreCase(status)) sb.append(" em andamento");

        String quem = (departamento != null && !departamento.isBlank()) ? departamento
                    : (responsavel != null && !responsavel.isBlank()) ? responsavel
                    : null;
        if (quem != null) sb.append(" de ").append(quem);

        return sb.append("...").toString();
    }

    /** 100.00 vira "100", mas 36.52 continua "36.52" — só mostra casa decimal quando o número não é inteiro. */
    private String formatarPercentualEnxuto(Object valor) {
        if (valor == null) return "—";
        double v = ((Number) valor).doubleValue();
        return v == Math.rint(v) ? String.format("%.0f", v) : String.format("%.2f", v);
    }

    /** Espaço normal em "U 2.1.2.22" deixa o navegador quebrar linha no meio do código dentro de
     * coluna estreita — troca por espaço não-quebrável pra o código sempre ficar numa linha só. */
    private String formatarCodigo(Object codigo) {
        return codigo == null ? "" : codigo.toString().replace(" ", " ");
    }

    /** data_final vem como java.sql.Date (ISO "2026-12-31") — exibe no formato brasileiro. */
    private String formatarData(Object data) {
        if (data == null) return "—";
        if (data instanceof java.sql.Date d) {
            return d.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        return data.toString();
    }
}