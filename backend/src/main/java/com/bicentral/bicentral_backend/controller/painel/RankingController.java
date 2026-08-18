package com.bicentral.bicentral_backend.controller.painel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bicentral.bicentral_backend.dto.painel.RankingDepartamentoDTO;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {
    private final JdbcTemplate jdbcTemplate;

    public RankingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<RankingDepartamentoDTO>> rankingGeral(
            @RequestParam(required = false) String tipoUnidade) {

        String sql = "SELECT departamento, tipo_unidade, " +
                "ROUND(AVG(percentual_execucao) * 100, 2) AS media_execucao_pct, " +
                "COUNT(*) AS qtd_acoes " +
                "FROM pat_execucao_departamento " +
                (tipoUnidade != null ? "WHERE tipo_unidade = ? " : "") +
                "GROUP BY departamento, tipo_unidade " +
                "ORDER BY media_execucao_pct DESC";

        List<Map<String, Object>> hoje = tipoUnidade != null
                ? jdbcTemplate.queryForList(sql, tipoUnidade)
                : jdbcTemplate.queryForList(sql);

        Map<String, Integer> posicoesAnteriores = buscarPosicoesAnteriores(tipoUnidade);

        List<RankingDepartamentoDTO> resultado = new ArrayList<>();
        for (int i = 0; i < hoje.size(); i++) {
            Map<String, Object> linha = hoje.get(i);
            resultado.add(new RankingDepartamentoDTO(
                    (String) linha.get("departamento"),
                    (String) linha.get("tipo_unidade"),
                    ((Number) linha.get("media_execucao_pct")).doubleValue(),
                    ((Number) linha.get("qtd_acoes")).intValue(),
                    i + 1,
                    posicoesAnteriores.get((String) linha.get("departamento"))));
        }

        return ResponseEntity.ok(resultado);
    }

    private Map<String, Integer> buscarPosicoesAnteriores(String tipoUnidade) {
        java.sql.Date dataAnterior = jdbcTemplate.queryForObject(
                "SELECT MAX(data_snapshot) FROM ranking_pat_snapshots WHERE data_snapshot < CURRENT_DATE",
                java.sql.Date.class);

        if (dataAnterior == null) {
            return Map.of();
        }

        String sql = "SELECT departamento, media_execucao_pct " +
                     "FROM ranking_pat_snapshots " +
                     "WHERE data_snapshot = ? " +
                     (tipoUnidade != null ? "AND tipo_unidade = ? " : "") +
                     "ORDER BY media_execucao_pct DESC";

        List<Map<String, Object>> ontem = tipoUnidade != null
                ? jdbcTemplate.queryForList(sql, dataAnterior, tipoUnidade)
                : jdbcTemplate.queryForList(sql, dataAnterior);

        Map<String, Integer> posicoes = new HashMap<>();
        for (int i = 0; i < ontem.size(); i++) {
            posicoes.put((String) ontem.get(i).get("departamento"), i + 1);
        }
        return posicoes;
    }
}
