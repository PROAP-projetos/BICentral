package com.bicentral.bicentral_backend.service.ia.tools;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Em código, não no prompt: o modelo não percebe sozinho que várias "Marias" são pessoas diferentes.
final class DesambiguacaoResponsavel {

    private static final int MAX_PESSOAS_LISTADAS = 20;

    /** {@code nomeExato} preenchido = uma pessoa só; {@code mensagem} preenchida = várias, perguntar ao usuário. */
    record Resultado(String nomeExato, String mensagem) {
    }

    private DesambiguacaoResponsavel() {
    }

    /** {@code departamentosVisiveis} null = sem restrição (mesma convenção do ConvidadoService). */
    static Resultado resolver(JdbcTemplate jdbcTemplate, String responsavel, List<String> departamentosVisiveis) {
        String pedido = responsavel.trim();
        List<Object> params = new ArrayList<>();
        params.add("%" + pedido + "%");
        String filtroVisibilidade = "";
        if (departamentosVisiveis != null && !departamentosVisiveis.isEmpty()) {
            filtroVisibilidade = " AND LOWER(departamento) IN ("
                    + String.join(",", Collections.nCopies(departamentosVisiveis.size(), "?")) + ")\n";
            params.addAll(departamentosVisiveis);
        }

        List<Map<String, Object>> pessoas = jdbcTemplate.queryForList("""
                SELECT dados_completos->>'Responsável' AS responsavel,
                       string_agg(DISTINCT departamento, '; ') AS departamentos,
                       COUNT(*) AS tarefas
                FROM pat_tarefas
                WHERE dados_completos->>'Responsável' ILIKE ?
                """ + filtroVisibilidade + """
                GROUP BY 1
                ORDER BY 3 DESC, 1
                """, params.toArray());

        if (pessoas.isEmpty()) {
            return new Resultado(null, null);
        }
        if (pessoas.size() == 1) {
            return new Resultado((String) pessoas.get(0).get("responsavel"), null);
        }
        // Nome completo digitado que também é pedaço do nome de outra pessoa: fica com o exato.
        for (Map<String, Object> p : pessoas) {
            if (pedido.equalsIgnoreCase((String) p.get("responsavel"))) {
                return new Resultado((String) p.get("responsavel"), null);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("VÁRIAS PESSOAS: ").append(pessoas.size()).append(" responsáveis têm '").append(pedido)
          .append("' no nome. Não junte as tarefas delas como se fossem de uma pessoa só. Pergunte ao usuário de qual")
          .append(" pessoa ele está falando, mostrando a lista abaixo (responsável, departamento e quantidade de tarefas),")
          .append(" e depois busque de novo passando o responsável exatamente como está na lista. Se ele quiser ver as")
          .append(" tarefas de um setor inteiro, busque por departamento.\n\n")
          .append("| Responsável | Departamento | Tarefas |\n|---|---|---|\n");
        for (Map<String, Object> p : pessoas.subList(0, Math.min(pessoas.size(), MAX_PESSOAS_LISTADAS))) {
            sb.append("| ").append(p.get("responsavel"))
              .append(" | ").append(p.get("departamentos"))
              .append(" | ").append(p.get("tarefas")).append(" |\n");
        }
        if (pessoas.size() > MAX_PESSOAS_LISTADAS) {
            sb.append("\n... e mais ").append(pessoas.size() - MAX_PESSOAS_LISTADAS)
              .append(" pessoa(s). Se a pessoa não estiver na lista, peça o nome completo ao usuário.");
        }
        return new Resultado(null, sb.toString());
    }
}
