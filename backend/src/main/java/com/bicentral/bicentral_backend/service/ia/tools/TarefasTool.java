package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TarefasTool {

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;

    public TarefasTool(JdbcTemplate jdbcTemplate, UsuarioService usuarioService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
    }

    @Tool("Busca as tarefas do PAT sob responsabilidade do usuário atualmente logado no chat. Use quando o usuário perguntar 'minhas tarefas', 'o que eu tenho pra fazer', 'como estão minhas pendências', ou pedir um panorama pessoal do próprio trabalho.")
    public String buscarMinhasTarefas() {
        System.out.println(">>> TOOL CHAMADA: buscarMinhasTarefas()");

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
            """, "%" + nomeResponsavel.trim() + "%");

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s) para " + nomeResponsavel);

        if (tarefas.isEmpty()) {
            return "Nenhuma tarefa encontrada para " + nomeResponsavel + " no PAT atual.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Tarefas de ").append(nomeResponsavel).append(" (").append(tarefas.size()).append(" no total):\n\n");
        sb.append("| Ação | Tarefa | % | Prazo |\n");
        sb.append("|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            boolean atrasada = Boolean.TRUE.equals(t.get("atrasada"));
            String prefixo = atrasada ? "⚠️ ATRASADA (" + t.get("dias_atraso") + "d) — " : "";

            sb.append("| ").append(t.get("codigo_acao"))
              .append(" | ").append(prefixo).append(t.get("titulo_tarefa"))
              .append(" | ").append(t.get("percentual")).append("%")
              .append(" | ").append(t.get("data_final"))
              .append(" |\n");
        }
        return sb.toString();
    }

    @Tool("Busca as tarefas de um departamento específico no PAT, com título, responsável e percentual de conclusão de cada uma. Use para perguntas sobre pendências operacionais de uma unidade, mais granular que o relatório de desempenho.")
    public String buscarTarefasPorDepartamento(@P("nome do departamento") String departamento) {
        System.out.println(">>> TOOL CHAMADA: buscarTarefasPorDepartamento(departamento=" + departamento + ")");

        List<Map<String, Object>> tarefas = jdbcTemplate.queryForList("""
            SELECT
                dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                substring(dados_completos->>'ITEM DO PAT' from '[A-Z]+ [0-9]+(?:\\.[0-9]+)*') AS codigo_acao,
                dados_completos->>'Responsável' AS responsavel,
                NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual,
                to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') AS data_final
            FROM pat_tarefas
            WHERE departamento ILIKE ?
            ORDER BY percentual ASC NULLS LAST
            LIMIT 20
            """, "%" + departamento.trim() + "%");

        System.out.println(">>> TOOL RESULTADO: " + tarefas.size() + " tarefa(s)");

        if (tarefas.isEmpty()) {
            return "Nenhuma tarefa encontrada para o departamento '" + departamento + "'";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| Ação | Tarefa | Responsável | % | Prazo |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Map<String, Object> t : tarefas) {
            sb.append("| ").append(t.get("codigo_acao"))
              .append(" | ").append(t.get("titulo_tarefa"))
              .append(" | ").append(t.get("responsavel"))
              .append(" | ").append(t.get("percentual")).append("%")
              .append(" | ").append(t.get("data_final"))
              .append(" |\n");
        }
        return sb.toString();
    }
}