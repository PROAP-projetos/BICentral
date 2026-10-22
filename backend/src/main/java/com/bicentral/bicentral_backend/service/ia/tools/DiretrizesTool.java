package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.IndicadorInepDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.ItemIesgoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.PraticaIesgoDTO;
import com.bicentral.bicentral_backend.service.admin.ConvidadoService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.DiretrizesService;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
        System.out.println(">>> TOOL CHAMADA: compararTarefasComDiretrizes(minhas=" + minhas + ", responsavel=" + responsavel
                + ", departamento=" + departamento + ", codigoAcao=" + codigoAcao + ", pagina=" + paginaAtual + ")");

        if (!minhas && vazio(responsavel) && vazio(departamento) && vazio(codigoAcao)) {
            return "Escopo não informado. Pergunte ao usuário de quem são as tarefas a analisar: dele mesmo, de uma pessoa,"
                    + " de um departamento ou de uma ação.";
        }

        Long usuarioId = usuarioService.buscarPorEmail(
                SecurityContextHolder.getContext().getAuthentication().getName()).getId();

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");

        String escopo;
        if (minhas) {
            String nome = nomeResponsavelDoUsuario(usuarioId);
            if (nome == null) {
                return "O usuário atual ainda não tem um nome de responsável vinculado no sistema. "
                        + "Um administrador precisa cadastrar esse vínculo no painel admin para essa consulta funcionar.";
            }
            where.append(" AND t.dados_completos->>'Responsável' ILIKE ? ");
            params.add("%" + nome.trim() + "%");
            escopo = "tarefas de " + nome;
        } else {
            // Mesma regra de buscarTarefas: convidado só vê tarefas dos departamentos que gerencia.
            List<String> departamentosVisiveis = convidadoService.departamentosComPessoasVisiveis(usuarioId);
            if (departamentosVisiveis != null) {
                String pedido = vazio(departamento) ? "" : departamento.trim().toLowerCase();
                boolean pediuOutroSetor = !pedido.isEmpty() && departamentosVisiveis.stream().noneMatch(d -> d.contains(pedido));
                if (departamentosVisiveis.isEmpty() || pediuOutroSetor) {
                    return TarefasTool.PREFIXO_SEM_PERMISSAO + " o usuário não tem permissão de acesso às tarefas"
                            + (pedido.isEmpty() ? "" : " de " + departamento.trim())
                            + " (convidados só veem as tarefas dos departamentos que gerenciam). Diga isso diretamente na primeira frase.";
                }
                where.append(" AND LOWER(t.departamento) IN (")
                     .append(String.join(",", Collections.nCopies(departamentosVisiveis.size(), "?")))
                     .append(") ");
                params.addAll(departamentosVisiveis);
            }
            escopo = "tarefas";
        }
        if (!vazio(responsavel)) {
            where.append(" AND t.dados_completos->>'Responsável' ILIKE ? ");
            params.add("%" + responsavel.trim() + "%");
            escopo += " de " + responsavel.trim();
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

    private void anexarAcao(StringBuilder sb, String codigo, List<Map<String, Object>> tarefas, boolean mostrarPessoa) {
        Optional<DiretrizesAcaoDTO> encontrada = diretrizesService.buscar(codigo);
        sb.append("### Ação ").append(codigo.isEmpty() ? "(sem código)" : codigo);
        encontrada.ifPresent(a -> sb.append(" — ").append(a.acao()));
        sb.append("\n");

        if (encontrada.isEmpty() || (vazio(encontrada.get().inep()) && vazio(encontrada.get().iesgo()))) {
            sb.append("Sem diretrizes INEP/IESGo cadastradas na base para esta ação: não dá para avaliar o alinhamento.\n");
        } else {
            DiretrizesAcaoDTO acao = encontrada.get();
            sb.append("INEP: ").append(vazio(acao.inep()) ? "nenhum" : acao.inep()).append("\n");
            for (IndicadorInepDTO indicador : listaOuVazia(acao.inepDetalhes())) {
                sb.append("- ").append(indicador.codigo()).append(" ").append(indicador.indicador())
                  .append(": ").append(indicador.criterio()).append("\n");
            }
            sb.append("IESGo: ").append(vazio(acao.iesgo()) ? "nenhum" : acao.iesgo()).append("\n");
            for (PraticaIesgoDTO pratica : listaOuVazia(acao.iesgoDetalhes())) {
                sb.append("- ").append(pratica.codigo()).append(":");
                for (ItemIesgoDTO item : listaOuVazia(pratica.itens())) {
                    sb.append(" (").append(item.item()).append(") ").append(item.texto());
                }
                sb.append("\n");
            }
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

    private static <T> List<T> listaOuVazia(List<T> lista) {
        return lista == null ? List.of() : lista;
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
