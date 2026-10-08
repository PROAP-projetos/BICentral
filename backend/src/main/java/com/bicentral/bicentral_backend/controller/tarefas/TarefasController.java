package com.bicentral.bicentral_backend.controller.tarefas;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.dto.tarefas.TarefasAtrasadasResumoDTO;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/tarefas")
public class TarefasController {
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;

    public TarefasController(JdbcTemplate jdbcTemplate, UsuarioService usuarioService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
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
