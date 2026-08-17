package com.bicentral.bicentral_backend.service.ia;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class RelatorioContextoTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioService usuarioService;
    private final RelatorioService relatorioService;
    private final EstadoSessao estadoSessao;

    public RelatorioContextoTool(JdbcTemplate jdbcTemplate, UsuarioService usuarioService, RelatorioService relatorioService, EstadoSessao estadoSessao) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
        this.relatorioService = relatorioService;
        this.estadoSessao = estadoSessao;
    }

    @Tool("Solicita a geração de um relatório de desempenho completo em DOCX para um departamento. Use quando o usuário pedir para 'gerar', 'criar' ou 'fazer' um relatório. O relatório demora cerca de 20-30 segundos para ficar pronto e ficará disponível no ícone de documentos no topo da tela; nesse histórico o usuário também pode abrir uma versão PDF para visualização.")
    public String solicitarGeracaoRelatorio(
            @P("nome do departamento, ex: PROEST") String departamento,
            @P(value = "'PAT' para execução do ano corrente, 'PDI' para acumulado de 5 anos, 'COMPARATIVO' para os dois. Se o usuário não especificar, use 'PAT'.", required = false) String tipo) {

        String tipoFinal = (tipo == null || tipo.isBlank()) ? "PAT" : tipo.toUpperCase().trim();
        System.out.println(">>> TOOL CHAMADA: solicitarGeracaoRelatorio(departamento=" + departamento + ", tipo=" + tipoFinal + ")");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        relatorioService.solicitarRelatorio(usuario.getId(), departamento.trim(), tipoFinal);
        estadoSessao.setRelatorioGerado(true);

        return "Relatório solicitado! Já abri o painel de relatórios pra você acompanhar — deve ficar pronto em cerca de 20-30 segundos.";
    }

    @Tool("Busca o texto completo do último relatório de desempenho que o usuário atual gerou. Use quando o usuário perguntar algo sobre 'o relatório que gerei', 'aquele relatório', ou pedir para comentar/explicar/aprofundar algo do relatório recém-criado.")
    public String buscarUltimoRelatorioGerado() {
        System.out.println(">>> TOOL CHAMADA: buscarUltimoRelatorioGerado()");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        List<Map<String, Object>> resultado = jdbcTemplate.queryForList("""
            SELECT departamento, tipo, status, texto_relatorio, criado_em
            FROM relatorios_gerados
            WHERE usuario_id = ?
            ORDER BY criado_em DESC
            LIMIT 1
            """, usuario.getId());

        if (resultado.isEmpty()) {
            return "O usuário ainda não gerou nenhum relatório nesta conta.";
        }

        Map<String, Object> ultimo = resultado.get(0);
        String status = (String) ultimo.get("status");

        if ("PROCESSANDO".equals(status)) {
            return "O último relatório solicitado (" + ultimo.get("departamento") + ", " + ultimo.get("tipo") + ") ainda está sendo processado. Peça para o usuário aguardar.";
        }
        if ("ERRO".equals(status)) {
            return "O último relatório solicitado (" + ultimo.get("departamento") + ") falhou ao ser gerado.";
        }

        String json = (String) ultimo.get("texto_relatorio");
        if (json == null || json.isBlank()) {
            return "O último relatório existe mas não há dados disponíveis para consulta.";
        }

        try {
            RelatorioEstruturadoDTO estruturado = MAPPER.readValue(json, RelatorioEstruturadoDTO.class);
            String texto = estruturado.paraTextoLegivel();
            System.out.println(">>> TOOL RESULTADO: relatório de " + ultimo.get("departamento") + " recuperado (" + texto.length() + " caracteres)");
            return "RELATÓRIO MAIS RECENTE (" + ultimo.get("departamento") + ", " + ultimo.get("tipo") + "):\n\n" + texto;
        } catch (Exception e) {
            return "O último relatório existe mas houve falha ao ler seu conteúdo.";
        }
    }
}
