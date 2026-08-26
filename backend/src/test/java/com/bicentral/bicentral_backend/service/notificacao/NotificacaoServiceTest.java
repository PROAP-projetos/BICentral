package com.bicentral.bicentral_backend.service.notificacao;

import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.PainelAtrasosDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private NotificacaoService notificacaoService;

    @BeforeEach
    void setUp() {
        notificacaoService = new NotificacaoService(jdbcTemplate);
    }

    @Test
    void gerarNotificacoes_ComTendenciaDeMelhora_DeveFormatarPercentuaisSemDecimaisZeros() {
        // Configurações de limite
        Map<String, Object> limites = Map.of("limite_baixo_pct", 40.0, "limite_bom_pct", 75.0);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(limites);

        // Snapshots fictícios (2 datas)
        Date d1 = Date.valueOf("2026-08-01");
        Date d2 = Date.valueOf("2026-07-01");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(d1, d2));

        // Resultado da consulta de departamentos
        Map<String, Object> row = new HashMap<>();
        row.put("departamento", "Orçamento");
        row.put("media_atual", 75.0);
        row.put("media_anterior", 60.0);

        when(jdbcTemplate.queryForList(anyString(), eq(d1), eq(d2), eq(1L)))
                .thenReturn(List.of(row));

        List<NotificacaoDTO> notificacoes = notificacaoService.gerarNotificacoes(1L);

        assertEquals(1, notificacoes.size());
        NotificacaoDTO notif = notificacoes.get(0);
        assertEquals("📈", notif.emoji());
        assertEquals("Orçamento", notif.departamento());
        assertTrue(notif.mensagem().contains("subiu de 60% para 75% no PAT."),
                "Esperado '60%' e '75%' sem '.0', mas foi: " + notif.mensagem());
    }

    @Test
    void gerarNotificacoes_ComTendenciaDeQueda_DeveIncluirTarefasCriticas() {
        Map<String, Object> limites = Map.of("limite_baixo_pct", 40.0, "limite_bom_pct", 75.0);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(limites);

        Date d1 = Date.valueOf("2026-08-01");
        Date d2 = Date.valueOf("2026-07-01");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(d1, d2));

        Map<String, Object> row = new HashMap<>();
        row.put("departamento", "TI");
        row.put("media_atual", 45.0);
        row.put("media_anterior", 65.0);

        when(jdbcTemplate.queryForList(anyString(), eq(d1), eq(d2), eq(1L)))
                .thenReturn(List.of(row));

        // Tarefa crítica simulada
        Map<String, Object> tarefaRow = new HashMap<>();
        tarefaRow.put("titulo_tarefa", "Atualizar Servidores");
        tarefaRow.put("responsavel", "Lean");
        tarefaRow.put("data_final", Date.valueOf("2026-08-10"));

        when(jdbcTemplate.queryForList(anyString(), eq("%TI%"), eq(3)))
                .thenReturn(List.of(tarefaRow));

        List<NotificacaoDTO> notificacoes = notificacaoService.gerarNotificacoes(1L);

        assertEquals(1, notificacoes.size());
        NotificacaoDTO notif = notificacoes.get(0);
        assertEquals("📉", notif.emoji());
        assertEquals("TI", notif.departamento());
        assertTrue(notif.mensagem().contains("caiu de 65% para 45% no PAT."));
        assertEquals(1, notif.tarefas().size());
        assertEquals("Atualizar Servidores", notif.tarefas().get(0).titulo());
        assertEquals("10/08/2026", notif.tarefas().get(0).prazo());
    }

    @Test
    void gerarPainelAtrasos_DeveMontarGraficoETabela() {
        Map<String, Object> tarefa = new HashMap<>();
        tarefa.put("titulo_tarefa", "Relatório de Gastos");
        tarefa.put("responsavel", "Neci");
        tarefa.put("data_final", Date.valueOf("2026-08-05"));

        when(jdbcTemplate.queryForList(anyString(), eq("%Financeiro%")))
                .thenReturn(List.of(tarefa));

        PainelAtrasosDTO painel = notificacaoService.gerarPainelAtrasos("Financeiro");

        assertNotNull(painel);
        assertEquals("Financeiro", painel.departamento());
        assertNotNull(painel.grafico());
        assertEquals(1, painel.tarefas().size());
        assertEquals("Relatório de Gastos", painel.tarefas().get(0).titulo());
    }
}
