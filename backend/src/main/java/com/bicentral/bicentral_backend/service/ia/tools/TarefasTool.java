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
        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
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

        StringBuilder sb = new StringBuilder();
        boolean truncado = totalTarefas != null && totalTarefas > tarefas.size();
        sb.append("Tarefas de ").append(nomeResponsavel).append(" (")
          .append(truncado ? tarefas.size() + " mais urgentes de " + totalTarefas + " no total" : tarefas.size() + " no total")
          .append("):\n\n");
        sb.append("| Ação | Tarefa | % | Atraso | Prazo |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            boolean atrasada = Boolean.TRUE.equals(t.get("atrasada"));
            String atraso = atrasada ? "⚠️ " + t.get("dias_atraso") + "d" : "—";

            sb.append("| ").append(formatarCodigo(t.get("codigo_acao")))
              .append(" | ").append(t.get("titulo_tarefa"))
              .append(" | ").append(formatarPercentualEnxuto(t.get("percentual"))).append("%")
              .append(" | ").append(atraso)
              .append(" | ").append(formatarData(t.get("data_final")))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Busca tarefas do PAT (ano corrente) com filtros combináveis: departamento, responsável, palavra-chave no título e status (atrasada/concluída/em andamento). Combine quantos filtros fizerem sentido pra pergunta — ex: tarefas atrasadas de um departamento sobre um assunto específico. Sempre informa o total real de tarefas encontradas, mesmo quando mostra só uma amostra por causa do limite. Use pra qualquer LISTAGEM de tarefas que não seja sobre o próprio usuário logado (aí use buscarMinhasTarefas) nem uma CONTAGEM agregada por departamento/UG (aí use contarTarefasPorDepartamento).")
    public String buscarTarefas(
            @P(value = "nome ou parte do nome do departamento, opcional", required = false) String departamento,
            @P(value = "nome ou parte do nome do responsável pela tarefa, opcional", required = false) String responsavel,
            @P(value = "palavra-chave a buscar no título da tarefa, opcional", required = false) String palavraChave,
            @P(value = "'atrasada' (prazo vencido e não concluída), 'concluida' ou 'em_andamento' (dentro do prazo, não concluída); deixe null para qualquer status", required = false) String status,
            @P(value = "'prazo' (mais urgentes primeiro, padrão) ou 'percentual' (menor conclusão primeiro)", required = false) String ordenarPor,
            @P(value = "quantidade máxima de tarefas a retornar, padrão 10 se o usuário não especificar. Se o usuário pedir 'todas', 'sem limite' ou 'lista tudo', passe 500. Se pedir uma quantidade específica (ex: 'as 30 mais urgentes'), passe esse número exato — nunca mostre menos do que foi pedido.", required = false) Integer limite) {

        int qtd = (limite == null || limite <= 0) ? 10 : Math.min(limite, 500);
        boolean ordenarPorPercentual = "percentual".equalsIgnoreCase(ordenarPor);
        statusExecucao.definir(montarStatusBusca(departamento, responsavel, status));
        System.out.println(">>> TOOL CHAMADA: buscarTarefas(departamento=" + departamento + ", responsavel=" + responsavel
            + ", palavraChave=" + palavraChave + ", status=" + status + ", ordenarPor=" + ordenarPor + ", limite=" + qtd + ")");

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (departamento != null && !departamento.isBlank()) {
            where.append(" AND b.departamento ILIKE ? ");
            params.add("%" + departamento.trim() + "%");
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

        // CTE "base" pré-calcula os campos derivados uma vez só, pra WHERE/COUNT/SELECT poderem
        // reaproveitar o mesmo alias (b.xxx) sem repetir as expressões regex/data em cada lugar.
        String baseSql = """
            WITH base AS (
                SELECT
                    substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                    dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    departamento,
                    dados_completos->>'Responsável' AS responsavel,
                    NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual_tarefa,
                    to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final
                FROM pat_tarefas
            )
            """;

        Integer total = jdbcTemplate.queryForObject(
            baseSql + "SELECT COUNT(*) FROM base b" + where, Integer.class, params.toArray());

        if (total == null || total == 0) {
            return "Nenhuma tarefa encontrada com esses filtros.";
        }

        String ordem = ordenarPorPercentual
            ? "b.percentual_tarefa ASC NULLS LAST"
            : "(b.data_final < CURRENT_DATE AND COALESCE(b.percentual_tarefa, 0) < 100) DESC, b.data_final ASC NULLS LAST";

        String listaSql = baseSql + """
            SELECT
                b.codigo_acao, b.titulo_tarefa, b.departamento, b.responsavel,
                ROUND(e.percentual_execucao * 100, 2) AS percentual_acao,
                b.percentual_tarefa, b.data_final,
                (b.data_final < CURRENT_DATE AND COALESCE(b.percentual_tarefa, 0) < 100) AS atrasada,
                (CURRENT_DATE - b.data_final) AS dias_atraso
            FROM base b
            LEFT JOIN pat_execucao_departamento e ON e.codigo_acao = b.codigo_acao AND e.departamento = b.departamento
            """ + where + " ORDER BY " + ordem + " LIMIT ?";

        List<Object> listaParams = new ArrayList<>(params);
        listaParams.add(qtd);
        List<Map<String, Object>> tarefas = jdbcTemplate.queryForList(listaSql, listaParams.toArray());

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s) de " + total + " no total");

        StringBuilder sb = new StringBuilder();
        boolean truncado = total > tarefas.size();
        sb.append(truncado ? tarefas.size() + " tarefa(s) mostrada(s) de " + total + " no total" : total + " tarefa(s) encontrada(s)")
          .append(":\n\n");
        sb.append("| Ação | Tarefa | Departamento | Responsável | % Ação | % Tarefa | Atraso | Prazo |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            boolean atrasada = Boolean.TRUE.equals(t.get("atrasada"));
            String atraso = atrasada ? "⚠️ " + t.get("dias_atraso") + "d" : "—";
            Object percentualAcao = t.get("percentual_acao");
            sb.append("| ").append(formatarCodigo(t.get("codigo_acao")))
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