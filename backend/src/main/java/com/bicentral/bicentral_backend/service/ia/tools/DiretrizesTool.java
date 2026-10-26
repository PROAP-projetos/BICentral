package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.service.ia.lgpd.LogSemNome;
import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.bicentral.bicentral_backend.service.admin.ConvidadoService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.AlinhamentoDiretrizesService;
import com.bicentral.bicentral_backend.service.ia.DiretrizesService;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DiretrizesTool {

    // Cada ação leva ~6 mil caracteres de diretrizes; 8 por chamada mantém a resposta legível e o custo
    // controlado para quem tem dezenas de ações (o resto vem nas páginas seguintes).
    static final int ACOES_POR_PAGINA = 8;
    private static final int TAREFAS_POR_ACAO = 15;
    private static final int MAX_DESCRICAO = 300;

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;
    private final ConvidadoService convidadoService;
    private final StatusExecucaoAgente statusExecucao;
    private final DiretrizesService diretrizesService;

    public DiretrizesTool(JdbcTemplate jdbcTemplate, UsuarioService usuarioService, ConvidadoService convidadoService,
            StatusExecucaoAgente statusExecucao, DiretrizesService diretrizesService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
        this.convidadoService = convidadoService;
        this.statusExecucao = statusExecucao;
        this.diretrizesService = diretrizesService;
    }

    @Tool("Compara tarefas do PAT com as diretrizes da ação a que cada uma está vinculada: indicadores do instrumento de avaliação institucional do INEP e práticas do questionário de governança IESGo (TCU). Use quando o usuário perguntar se tarefas estão de acordo, alinhadas ou atendem ao INEP e/ou IESGo, ou quais critérios INEP/IESGo uma tarefa ou ação cobre. Escopos: as tarefas do próprio usuário logado (minhasTarefas=true), de um responsável, de um departamento ou de uma ação; os filtros podem ser combinados. Devolve, por ação, as diretrizes e as tarefas para você comparar. Analisa no máximo 8 ações por chamada, das que têm mais tarefas para as que têm menos; para as seguintes, chame de novo com a próxima página.")
    public String compararTarefasComDiretrizes(
            @P(value = "true quando a pergunta é sobre as tarefas do próprio usuário logado ('minhas tarefas')", required = false) Boolean minhasTarefas,
            @P(value = "nome ou parte do nome do responsável pelas tarefas, opcional", required = false) String responsavel,
            @P(value = "nome ou parte do nome do departamento, opcional", required = false) String departamento,
            @P(value = "código da ação, com ou sem a letra na frente (ex: 'U 1.1.1.1' ou '1.1.1.1'), opcional", required = false) String codigoAcao,
            @P(value = "página de ações a analisar, começando em 1 (padrão). Use 2, 3... quando o usuário pedir para continuar com as próximas ações", required = false) Integer pagina) {

        boolean minhas = Boolean.TRUE.equals(minhasTarefas);
        int paginaAtual = (pagina == null || pagina < 1) ? 1 : pagina;
        statusExecucao.definir("Comparando tarefas com INEP e IESGo...");
        System.out.println(">>> TOOL CHAMADA: compararTarefasComDiretrizes(minhas=" + minhas + ", responsavel=" + LogSemNome.campo(responsavel)
                + ", departamento=" + departamento + ", codigoAcao=" + codigoAcao + ", pagina=" + paginaAtual + ")");

        if (!minhas && vazio(responsavel) && vazio(departamento) && vazio(codigoAcao)) {
            return "Sem recorte: para o PAT inteiro, use panoramaAlinhamentoDiretrizes sem filtros.";
        }

        Long usuarioId = usuarioService.buscarPorEmail(
                SecurityContextHolder.getContext().getAuthentication().getName()).getId();

        String nomeMinhas = null;
        if (minhas) {
            nomeMinhas = nomeResponsavelDoUsuario(usuarioId);
            if (nomeMinhas == null) {
                return "O usuário atual ainda não tem um nome de responsável vinculado no sistema. "
                        + "Um administrador precisa cadastrar esse vínculo no painel admin para essa consulta funcionar.";
            }
        }
        Filtro filtro = montarFiltro(usuarioId, nomeMinhas, responsavel, departamento, codigoAcao);
        if (filtro.erro() != null) {
            return filtro.erro();
        }
        StringBuilder where = filtro.where();
        List<Object> params = filtro.params();
        String escopo = filtro.escopo();

        List<Map<String, Object>> tarefas = jdbcTemplate.queryForList("""
            SELECT
                substring(t.dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                t.dados_completos->>'TÍTULO DA TAREFA' AS titulo,
                t.dados_completos->>'DESCRIÇÃO DA TAREFA' AS descricao,
                t.dados_completos->>'Responsável' AS responsavel,
                t.departamento,
                NULLIF(regexp_replace(t.dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual
            FROM pat_tarefas t
            """ + where + " ORDER BY titulo", params.toArray());

        if (tarefas.isEmpty()) {
            return "Nenhuma tarefa encontrada para o escopo pedido (" + escopo + ").";
        }

        // Agrupa por ação mantendo a ordem de chegada; depois ordena as ações por quantidade de tarefas.
        Map<String, List<Map<String, Object>>> porAcao = new LinkedHashMap<>();
        for (Map<String, Object> tarefa : tarefas) {
            String codigo = (String) tarefa.get("codigo_acao");
            porAcao.computeIfAbsent(codigo == null ? "" : codigo, c -> new ArrayList<>()).add(tarefa);
        }
        List<String> acoes = new ArrayList<>(porAcao.keySet());
        acoes.sort(Comparator.<String>comparingInt(c -> porAcao.get(c).size()).reversed()
                .thenComparing(DiretrizesTool::compararCodigo));

        int totalPaginas = (acoes.size() + ACOES_POR_PAGINA - 1) / ACOES_POR_PAGINA;
        int inicio = (paginaAtual - 1) * ACOES_POR_PAGINA;
        if (inicio >= acoes.size()) {
            return "Nenhuma página " + paginaAtual + ": são " + acoes.size() + " ações no total (" + totalPaginas + " página(s)).";
        }
        List<String> acoesDaPagina = acoes.subList(inicio, Math.min(inicio + ACOES_POR_PAGINA, acoes.size()));

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s) em " + acoes.size() + " ação(ões), página "
                + paginaAtual + "/" + totalPaginas);

        StringBuilder sb = new StringBuilder();
        sb.append("COMPARAÇÃO COM DIRETRIZES (base INEP/IESGo ").append(diretrizesService.versao()).append(") — ")
          .append(escopo).append(": ").append(tarefas.size()).append(" tarefa(s) em ").append(acoes.size())
          .append(" ação(ões). Esta página traz as ações ").append(inicio + 1).append(" a ").append(inicio + acoesDaPagina.size())
          .append(" (página ").append(paginaAtual).append(" de ").append(totalPaginas).append(").\n\n");

        boolean mostrarPessoa = !minhas;
        for (String codigo : acoesDaPagina) {
            anexarAcao(sb, codigo, porAcao.get(codigo), mostrarPessoa);
        }

        if (paginaAtual < totalPaginas) {
            int restantes = acoes.size() - (inicio + acoesDaPagina.size());
            sb.append("AINDA FALTAM ").append(restantes).append(" ação(ões). Diga isso ao usuário e ofereça continuar")
              .append(" (chamar de novo com pagina=").append(paginaAtual + 1).append(").\n");
        }
        return sb.toString();
    }

    @Tool("Panorama do alinhamento das tarefas do PAT com as diretrizes INEP e IESGo, a partir da análise já feita tarefa por tarefa e guardada no sistema. Use para o PAT como um todo ('o PAT de forma geral', 'todas as unidades', 'quais diretrizes nenhuma tarefa cobre') e para recortes grandes, como um departamento inteiro ('as tarefas da PROGRAD têm a ver com o INEP?'). Devolve quantas tarefas estão alinhadas, parciais ou sem evidência, a comparação entre departamentos, as diretrizes mais cobertas, as que nenhuma tarefa cobre e as tarefas sem evidência. Filtros opcionais; sem filtro, é o PAT inteiro. Se o panorama ainda não tiver sido gerado, a resposta diz o que fazer.")
    public String panoramaAlinhamentoDiretrizes(
            @P(value = "nome ou parte do nome do departamento, opcional", required = false) String departamento,
            @P(value = "nome ou parte do nome do responsável pelas tarefas, opcional", required = false) String responsavel,
            @P(value = "código da ação, com ou sem a letra na frente (ex: 'U 1.1.1.1' ou '1.1.1.1'), opcional", required = false) String codigoAcao) {

        statusExecucao.definir("Montando o panorama INEP e IESGo...");
        System.out.println(">>> TOOL CHAMADA: panoramaAlinhamentoDiretrizes(departamento=" + departamento
                + ", responsavel=" + LogSemNome.campo(responsavel) + ", codigoAcao=" + codigoAcao + ")");

        Long usuarioId = usuarioService.buscarPorEmail(
                SecurityContextHolder.getContext().getAuthentication().getName()).getId();
        Filtro filtro = montarFiltro(usuarioId, null, responsavel, departamento, codigoAcao);
        if (filtro.erro() != null) {
            return filtro.erro();
        }

        List<Object> params = new ArrayList<>();
        params.add(diretrizesService.versao());
        params.addAll(filtro.params());
        // Análise de outra versão da base ou de texto alterado depois conta como não analisada.
        List<PanoramaAlinhamento.Linha> linhas = jdbcTemplate.query("""
            SELECT t.id, t.departamento, %s AS codigo_acao,
                   t.dados_completos->>'TÍTULO DA TAREFA' AS titulo,
                   CASE WHEN a.versao_base = ? AND a.hash_texto = %s THEN a.situacao END AS situacao,
                   a.diretrizes, a.justificativa, a.analisado_em
            FROM pat_tarefas t
            LEFT JOIN alinhamento_tarefa a ON a.tarefa_id = t.id
            """.formatted(AlinhamentoDiretrizesService.SQL_CODIGO_ACAO, AlinhamentoDiretrizesService.SQL_HASH_TEXTO)
                + filtro.where() + " ORDER BY t.departamento, codigo_acao, t.id",
                (rs, i) -> new PanoramaAlinhamento.Linha(rs.getLong("id"), rs.getString("departamento"),
                        rs.getString("codigo_acao"), rs.getString("titulo"), rs.getString("situacao"),
                        rs.getString("diretrizes"), rs.getString("justificativa"),
                        rs.getObject("analisado_em", OffsetDateTime.class)),
                params.toArray());

        if (linhas.isEmpty()) {
            return "Nenhuma tarefa encontrada para o escopo pedido (" + filtro.escopo() + ").";
        }
        String escopo = filtro.escopo().equals("tarefas") ? "PAT inteiro" : filtro.escopo();
        System.out.println(">>> TOOL RESULTADO: panorama com " + linhas.size() + " tarefa(s)");
        return PanoramaAlinhamento.montar(escopo, linhas, diretrizesService);
    }

    /** {@code erro} preenchido = devolver ao agente em vez de consultar. */
    private record Filtro(StringBuilder where, List<Object> params, String escopo, String erro) {
        static Filtro falha(String erro) {
            return new Filtro(null, null, null, erro);
        }
    }

    /** {@code nomeMinhas} preenchido = tarefas do próprio usuário (sem filtro de convidado, são dele). */
    private Filtro montarFiltro(Long usuarioId, String nomeMinhas, String responsavel, String departamento, String codigoAcao) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");

        String escopo;
        List<String> departamentosVisiveis = null;
        if (nomeMinhas != null) {
            where.append(" AND t.dados_completos->>'Responsável' ILIKE ? ");
            params.add("%" + nomeMinhas.trim() + "%");
            escopo = "tarefas de " + nomeMinhas;
        } else {
            // Mesma regra de buscarTarefas: convidado só vê tarefas dos departamentos que gerencia.
            departamentosVisiveis = convidadoService.departamentosComPessoasVisiveis(usuarioId);
            if (departamentosVisiveis != null) {
                String pedido = vazio(departamento) ? "" : departamento.trim().toLowerCase();
                boolean pediuOutroSetor = !pedido.isEmpty() && departamentosVisiveis.stream().noneMatch(d -> d.contains(pedido));
                if (departamentosVisiveis.isEmpty() || pediuOutroSetor) {
                    return Filtro.falha(TarefasTool.PREFIXO_SEM_PERMISSAO + " o usuário não tem permissão de acesso às tarefas"
                            + (pedido.isEmpty() ? "" : " de " + departamento.trim())
                            + " (convidados só veem as tarefas dos departamentos que gerenciam). Diga isso diretamente na primeira frase.");
                }
                where.append(" AND LOWER(t.departamento) IN (")
                     .append(String.join(",", Collections.nCopies(departamentosVisiveis.size(), "?")))
                     .append(") ");
                params.addAll(departamentosVisiveis);
            }
            escopo = departamentosVisiveis != null && vazio(departamento) ? "tarefas dos seus departamentos" : "tarefas";
        }
        if (!vazio(responsavel)) {
            DesambiguacaoResponsavel.Resultado pessoa =
                    DesambiguacaoResponsavel.resolver(jdbcTemplate, responsavel, departamentosVisiveis);
            if (pessoa.mensagem() != null) {
                return Filtro.falha(pessoa.mensagem());
            }
            if (pessoa.nomeExato() == null) {
                return Filtro.falha("Nenhuma tarefa encontrada para " + responsavel.trim() + ".");
            }
            where.append(" AND t.dados_completos->>'Responsável' = ? ");
            params.add(pessoa.nomeExato());
            escopo += " de " + pessoa.nomeExato();
        }
        if (!vazio(departamento)) {
            where.append(" AND t.departamento ILIKE ? ");
            params.add("%" + departamento.trim() + "%");
            escopo += " do departamento " + departamento.trim();
        }
        if (!vazio(codigoAcao)) {
            where.append(" AND regexp_replace(substring(t.dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*'),"
                    + " '^[A-Za-zÀ-ÿ]+\\s*', '') = regexp_replace(?, '^[A-Za-zÀ-ÿ]+\\s*', '') ");
            params.add(codigoAcao.trim());
            escopo += " da ação " + codigoAcao.trim();
        }
        return new Filtro(where, params, escopo, null);
    }

    private void anexarAcao(StringBuilder sb, String codigo, List<Map<String, Object>> tarefas, boolean mostrarPessoa) {
        Optional<DiretrizesAcaoDTO> encontrada = diretrizesService.buscar(codigo);
        sb.append("### Ação ").append(codigo.isEmpty() ? "(sem código)" : codigo);
        encontrada.ifPresent(a -> sb.append(" — ").append(a.acao()));
        sb.append("\n");

        if (encontrada.isEmpty() || !DiretrizesService.temDiretrizes(encontrada.get())) {
            sb.append("Sem diretrizes INEP/IESGo cadastradas na base para esta ação: não dá para avaliar o alinhamento.\n");
        } else {
            sb.append(DiretrizesService.descrever(encontrada.get()));
        }

        sb.append("Tarefas (").append(tarefas.size()).append("):\n");
        for (Map<String, Object> tarefa : tarefas.subList(0, Math.min(TAREFAS_POR_ACAO, tarefas.size()))) {
            sb.append("- \"").append(tarefa.get("titulo")).append("\" — ")
              .append(formatarPercentual(tarefa.get("percentual"))).append(" concluído");
            if (mostrarPessoa) {
                sb.append(", ").append(tarefa.get("responsavel")).append(", ").append(tarefa.get("departamento"));
            }
            String descricao = (String) tarefa.get("descricao");
            sb.append(". Descrição: ").append(vazio(descricao) ? "(sem descrição)" : resumir(descricao.trim())).append("\n");
        }
        if (tarefas.size() > TAREFAS_POR_ACAO) {
            sb.append("(mais ").append(tarefas.size() - TAREFAS_POR_ACAO).append(" tarefa(s) desta ação não listadas)\n");
        }
        sb.append("\n");
    }

    private String nomeResponsavelDoUsuario(Long usuarioId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT nome_responsavel FROM usuario_responsavel WHERE usuario_id = ?", String.class, usuarioId);
        } catch (Exception e) {
            return null;
        }
    }

    /** Ordem numérica do código ("U 2.1.10" depois de "U 2.1.9"), não alfabética. */
    static int compararCodigo(String a, String b) {
        String[] pa = a.replaceFirst("^[A-Za-z]+\\s*", "").split("\\.");
        String[] pb = b.replaceFirst("^[A-Za-z]+\\s*", "").split("\\.");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int comparacao = Integer.compare(numero(pa[i]), numero(pb[i]));
            if (comparacao != 0) return comparacao;
        }
        return Integer.compare(pa.length, pb.length);
    }

    private static int numero(String parte) {
        try {
            return Integer.parseInt(parte);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String resumir(String texto) {
        return texto.length() <= MAX_DESCRICAO ? texto : texto.substring(0, MAX_DESCRICAO) + "...";
    }

    private static String formatarPercentual(Object valor) {
        if (valor == null) return "0%";
        double v = ((Number) valor).doubleValue();
        return (v == Math.rint(v) ? String.format("%.0f", v) : String.format("%.2f", v)) + "%";
    }
}
