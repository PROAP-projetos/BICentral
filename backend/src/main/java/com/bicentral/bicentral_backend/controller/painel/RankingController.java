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

import com.bicentral.bicentral_backend.dto.painel.CompartilhamentoDepartamentosDTO;
import com.bicentral.bicentral_backend.dto.painel.RankingAtualizacaoDTO;
import com.bicentral.bicentral_backend.dto.painel.RankingDepartamentoDTO;
import com.bicentral.bicentral_backend.dto.painel.RankingResumoDTO;

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
                "ROUND(COALESCE(AVG(percentual_execucao), 0) * 100, 2) AS media_execucao_pct, " +
                "COUNT(DISTINCT codigo_acao) AS qtd_acoes, " +
                "COUNT(DISTINCT codigo_acao) FILTER (WHERE percentual_execucao >= 1) AS qtd_acoes_concluidas " +
                "FROM pat_execucao_departamento " +
                (tipoUnidade != null ? "WHERE tipo_unidade = ? " : "") +
                "GROUP BY departamento, tipo_unidade " +
                "ORDER BY media_execucao_pct DESC, departamento";

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
                    ((Number) linha.get("qtd_acoes_concluidas")).intValue(),
                    i + 1,
                    posicoesAnteriores.get((String) linha.get("departamento"))));
        }

        return ResponseEntity.ok(resultado);
    }

    // Ação compartilhada conta uma vez; só é concluída se todas as unidades que respondem por ela chegaram a 100%.
    @GetMapping("/resumo")
    public ResponseEntity<RankingResumoDTO> resumo(@RequestParam(required = false) String tipoUnidade) {
        String sql = "SELECT COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE menor_execucao >= 1) AS concluidas " +
                "FROM (SELECT codigo_acao, MIN(COALESCE(percentual_execucao, 0)) AS menor_execucao " +
                "FROM pat_execucao_departamento WHERE codigo_acao IS NOT NULL " +
                (tipoUnidade != null ? "AND tipo_unidade = ? " : "") +
                "GROUP BY codigo_acao) acoes";

        Map<String, Object> linha = tipoUnidade != null
                ? jdbcTemplate.queryForMap(sql, tipoUnidade)
                : jdbcTemplate.queryForMap(sql);

        return ResponseEntity.ok(new RankingResumoDTO(
                ((Number) linha.get("total")).intValue(),
                ((Number) linha.get("concluidas")).intValue()));
    }

    @GetMapping("/atualizacao")
    public ResponseEntity<RankingAtualizacaoDTO> atualizacao() {
        String atualizadoEm = jdbcTemplate.queryForObject(
                "SELECT to_char(MAX(atualizado_em), 'DD/MM/YYYY HH24:MI') FROM pat_dados", String.class);
        return ResponseEntity.ok(new RankingAtualizacaoDTO(atualizadoEm));
    }

    private static final int LIMITE_LIGACOES = 80;

    @GetMapping("/compartilhamento")
    public ResponseEntity<List<CompartilhamentoDepartamentosDTO>> compartilhamento(
            @RequestParam(required = false) String tipoUnidade) {

        String sql = "SELECT a.departamento AS origem, b.departamento AS destino, " +
                "COUNT(DISTINCT a.codigo_acao) AS qtd_acoes, " +
                "ROUND(AVG(ABS(COALESCE(a.percentual_execucao, 0) - COALESCE(b.percentual_execucao, 0))) * 100, 1) AS diferenca_media_pct " +
                "FROM pat_execucao_departamento a " +
                "JOIN pat_execucao_departamento b ON a.codigo_acao = b.codigo_acao AND a.departamento < b.departamento " +
                "WHERE a.codigo_acao IS NOT NULL " +
                (tipoUnidade != null ? "AND a.tipo_unidade = ? AND b.tipo_unidade = ? " : "") +
                "GROUP BY a.departamento, b.departamento " +
                "ORDER BY qtd_acoes DESC, diferenca_media_pct DESC " +
                "LIMIT " + LIMITE_LIGACOES;

        List<Map<String, Object>> linhas = tipoUnidade != null
                ? jdbcTemplate.queryForList(sql, tipoUnidade, tipoUnidade)
                : jdbcTemplate.queryForList(sql);

        List<CompartilhamentoDepartamentosDTO> resultado = new ArrayList<>();
        for (Map<String, Object> linha : linhas) {
            resultado.add(new CompartilhamentoDepartamentosDTO(
                    (String) linha.get("origem"),
                    (String) linha.get("destino"),
                    ((Number) linha.get("qtd_acoes")).intValue(),
                    ((Number) linha.get("diferenca_media_pct")).doubleValue()));
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
                     "ORDER BY COALESCE(media_execucao_pct, 0) DESC, departamento";

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
