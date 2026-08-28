package com.bicentral.bicentral_backend.controller.tarefas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bicentral.bicentral_backend.dto.tarefas.TarefaGrafoDTO;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.dto.tarefas.TarefasAtrasadasResumoDTO;
import org.springframework.security.core.context.SecurityContextHolder;

// Endpoint REST puro (sem passar pelo agente de IA) pra expor as tarefas de um departamento —
// usado pelo grafo de atividades do frontend. A mesma consulta já existe como ferramenta da IA
// em TarefasTool.buscarTarefasPorDepartamento, mas aquela só o agente consegue chamar.
@RestController
@RequestMapping("/api/tarefas")
public class TarefasController {
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;

    public TarefasController(JdbcTemplate jdbcTemplate, UsuarioService usuarioService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<TarefaGrafoDTO>> listarPorDepartamento(@RequestParam String departamento) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                """
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
                        """,
                departamento);

        List<TarefaGrafoDTO> resultado = new ArrayList<>();
        for (Map<String, Object> linha : linhas) {
            Double percentual = linha.get("percentual") == null ? null
                    : ((Number) linha.get("percentual")).doubleValue();
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

    @GetMapping("/minhas-atrasadas")
    public ResponseEntity<TarefasAtrasadasResumoDTO> minhasAtrasadas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        String nomeResponsavel;
        try{
            nomeResponsavel = jdbcTemplate.queryForObject(
                "SELECT nome_responsavel FROM usuario_responsavel WHERE usuario_id = ?",
                String.class, usuario.getId());
           } catch (Exception e){
                return ResponseEntity.ok(new TarefasAtrasadasResumoDTO(0, null, null));
           }
        List<Map<String, Object>> atrasadas = jdbcTemplate.queryForList("""
                SELECT
                    dados_completos->>'TÍTULO DA TAREFA' AS titulo_tarefa,
                    (CURRENT_DATE - to_date(dados_completos->>'Data Final', 'DD/MM/YYYY')) AS dias_atraso
                FROM pat_tarefas
                WHERE dados_completos->>'Responsável' ILIKE ?
                    AND to_date(dados_completos->>'Data Final', 'DD/MM/YYYY') < CURRENT_DATE
                    AND NULLIF(regexp_replace(dados_completos->>'% Concluído', '[^0-9.,]', '', 'g'), '')::numeric < 100
                    ORDER BY dias_atraso DESC
                    """, "%" + nomeResponsavel.trim() + "%");
        
        if (atrasadas.isEmpty()){
            return ResponseEntity.ok(new TarefasAtrasadasResumoDTO(0, null, null));
        }

        Map<String, Object> maisUrgente = atrasadas.get(0);
        return ResponseEntity.ok(new TarefasAtrasadasResumoDTO(
            atrasadas.size(),
            (String) maisUrgente.get("titulo_tarefa"),
            ((Number) maisUrgente.get("dias_atraso")).intValue()
        ));
    }
}
