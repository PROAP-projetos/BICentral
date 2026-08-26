package com.bicentral.bicentral_backend.controller.painel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bicentral.bicentral_backend.dto.painel.TarefaGrafoDTO;

// Endpoint REST puro (sem passar pelo agente de IA) pra expor as tarefas de um departamento —
// usado pelo grafo de atividades do frontend. A mesma consulta já existe como ferramenta da IA
// em TarefasTool.buscarTarefasPorDepartamento, mas aquela só o agente consegue chamar.
@RestController
@RequestMapping("/api/tarefas")
public class TarefasController {
    private final JdbcTemplate jdbcTemplate;

    public TarefasController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<TarefaGrafoDTO>> listarPorDepartamento(@RequestParam String departamento) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
            SELECT
                dados_completos->>'TÍTULO DA TAREFA' AS titulo,
                dados_completos->>'Responsável' AS responsavel,
                NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric AS percentual,
                (to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                    AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100) AS atrasada
            FROM pat_tarefas
            WHERE departamento = ?
            ORDER BY percentual ASC NULLS LAST
            LIMIT 30
            """, departamento);

        List<TarefaGrafoDTO> resultado = new ArrayList<>();
        for (Map<String, Object> linha : linhas) {
            Double percentual = linha.get("percentual") == null ? null : ((Number) linha.get("percentual")).doubleValue();
            boolean atrasada = Boolean.TRUE.equals(linha.get("atrasada"));

            String status;
            if (atrasada) {
                status = "atrasada";
            } else if (percentual != null && percentual >= 100) {
                status = "concluida";
            } else {
                status = "em_andamento";
            }

            resultado.add(new TarefaGrafoDTO(
                    (String) linha.get("titulo"),
                    (String) linha.get("responsavel"),
                    percentual,
                    status));
        }

        return ResponseEntity.ok(resultado);
    }
}
