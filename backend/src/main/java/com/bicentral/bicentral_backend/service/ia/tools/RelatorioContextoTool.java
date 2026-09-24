package com.bicentral.bicentral_backend.service.ia.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioPessoaDTO;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.RelatorioService;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;
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
    private final StatusExecucaoAgente statusExecucao;

    public RelatorioContextoTool(JdbcTemplate jdbcTemplate, UsuarioService usuarioService, RelatorioService relatorioService, EstadoSessao estadoSessao, StatusExecucaoAgente statusExecucao) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioService = usuarioService;
        this.relatorioService = relatorioService;
        this.estadoSessao = estadoSessao;
        this.statusExecucao = statusExecucao;
    }

    @Tool("Solicita a geração de um relatório de desempenho completo para um departamento, em DOCX, PDF ou Excel. Use quando o usuário pedir para 'gerar', 'criar' ou 'fazer' um relatório. O relatório demora cerca de 20-30 segundos para ficar pronto e ficará disponível no ícone de documentos no topo da tela; nesse histórico o usuário também pode abrir uma versão PDF para visualização, independente do formato original.")
    public String solicitarGeracaoRelatorio(
            @P("nome do departamento, ex: PROEST") String departamento,
            @P(value = "sempre 'PAT' por enquanto — é o único tipo disponível (execução do ano corrente). Relatório de PDI/acumulado ainda não está disponível, mesma limitação das ferramentas de consulta.", required = false) String tipo,
            @P(value = "true se o usuário pedir explicitamente pra ver o NOME (ou 'descrição') da ação em vez do código (ex: 'mostra o nome da ação', 'não quero só o código', 'quero a descrição') na tabela de Pontos de Atenção do relatório. false/omitido mantém o padrão (código).", required = false) Boolean mostrarNomeAcao,
            @P(value = "'crescente' ou 'decrescente' SOMENTE se o usuário pedir explicitamente uma lista/tabela de TODAS as ações ordenada por percentual de execução (ex: 'ordem crescente', 'do menor pro maior', 'lista completa ordenada'). Omitido/null quando o usuário só pede o relatório normal, sem essa lista completa — nesse caso o relatório NÃO ganha a seção extra.", required = false) String ordenacao,
            @P(value = "'DOCX', 'PDF' ou 'XLSX'/'EXCEL' — formato pedido pelo usuário (ex: 'em excel', 'quero em planilha', 'gera o pdf'). Omitido/null mantém o padrão DOCX.", required = false) String formato,
            @P(value = "true SOMENTE se o usuário pedir explicitamente pra ver as tarefas de cada ação dentro do relatório (ex: 'quero as ações e as tarefas', 'mostra as tarefas de cada ação', 'com as tarefas'). Adiciona uma seção nova com todas as ações do departamento e suas tarefas — deixa o relatório bem maior, por isso só ativa quando pedido claramente, não por padrão.", required = false) Boolean incluirTarefas,
            @P(value = "lista separada por vírgula das seções que o usuário quer no relatório, SOMENTE se ele pedir explicitamente pra restringir/escolher seções (ex: 'só quero pontos de atenção e a lista completa', 'sem a metodologia'). Chaves válidas: 'visao_executiva' (números e distribuição gerais), 'pontos_atencao', 'destaques', 'leitura_cenario' (resumo/análise em texto), 'acompanhamento', 'detalhamento' (tarefas das ações mais críticas), 'lista_completa' (exige também pedir ordenação, ou a lista some — se o usuário só falou 'lista completa' sem mais nada, ainda assim inclua 'lista_completa' aqui que o sistema resolve a ordenação sozinho), 'tarefas_por_acao', 'metodologia'. Omitido/null = relatório completo padrão, com todas as seções (comportamento de sempre) — NUNCA restrinja seção por conta própria sem o usuário ter pedido.", required = false) String secoes,
            @P(value = "palavra-chave de marcador/tag pra filtrar quais ações entram no relatório (ex: 'risco', 'CPA', 'AUDIN', 'Plano de Governo') — SOMENTE se o usuário pedir explicitamente um recorte por marcador (ex: 'só as ações de risco', 'filtrando por CPA'). Omitido/null = todas as ações do departamento, sem filtro (padrão).", required = false) String marcador) {

        // RelatorioService.solicitarRelatorio já força "PAT" internamente independente do que
        // vier aqui (defesa em profundidade, cobre também o endpoint REST direto) — mas nem
        // oferece a opção pro LLM pedir, pra não fingir uma capacidade que não existe de verdade.
        String tipoFinal = (tipo == null || tipo.isBlank()) ? "PAT" : tipo.toUpperCase().trim();
        boolean mostrarNomeAcaoFinal = Boolean.TRUE.equals(mostrarNomeAcao);
        String ordenacaoFinal = (ordenacao == null || ordenacao.isBlank()) ? null : ordenacao.trim().toLowerCase();
        boolean incluirTarefasFinal = Boolean.TRUE.equals(incluirTarefas);
        List<String> secoesFinal = (secoes == null || secoes.isBlank())
                ? List.of()
                : java.util.Arrays.stream(secoes.split(",")).map(String::trim).map(String::toLowerCase).filter(s -> !s.isBlank()).toList();
        String marcadorFinal = (marcador == null || marcador.isBlank()) ? null : marcador.trim();
        statusExecucao.definir("Solicitando o relatório de " + departamento + (incluirTarefasFinal ? " (com tarefas por ação)" : "") + "...");
        System.out.println(">>> TOOL CHAMADA: solicitarGeracaoRelatorio(departamento=" + departamento + ", tipo=" + tipoFinal + ", mostrarNomeAcao=" + mostrarNomeAcaoFinal + ", ordenacao=" + ordenacaoFinal + ", formato=" + formato + ", incluirTarefas=" + incluirTarefasFinal + ", secoes=" + secoesFinal + ", marcador=" + marcadorFinal + ")");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        relatorioService.solicitarRelatorio(usuario.getId(), departamento.trim(), tipoFinal, formato, mostrarNomeAcaoFinal, ordenacaoFinal, incluirTarefasFinal, secoesFinal, marcadorFinal);
        estadoSessao.setRelatorioGerado(true);

        return "Relatório solicitado! Já abri o painel de relatórios pra você acompanhar — deve ficar pronto em cerca de 20-30 segundos.";
    }

    @Tool("Solicita a geração de um relatório de desempenho sobre uma PESSOA específica (suas tarefas do PAT), não sobre um departamento — em DOCX, PDF ou Excel. Use quando o usuário pedir 'um relatório sobre mim', 'sobre [nome de alguém]', 'das minhas tarefas' em formato de relatório (não uma simples consulta no chat). Sujeito a permissão: só é liberado pra pessoa sobre si mesma, um admin, ou quem gerencia o departamento da pessoa-alvo — se negado, explique o motivo devolvido sem tentar contornar.")
    public String solicitarRelatorioPessoa(
            @P(value = "nome da pessoa (pode ser parcial) — ex: 'Idelma'. Se o usuário disser 'sobre mim', 'meu relatório' ou 'minhas tarefas', deixe este campo VAZIO/null — o sistema já sabe quem está logado, NÃO peça pra ela confirmar o próprio nome.", required = false) String nomePessoa,
            @P(value = "'DOCX', 'PDF' ou 'XLSX'/'EXCEL' — formato pedido pelo usuário. Omitido/null mantém o padrão DOCX.", required = false) String formato) {

        statusExecucao.definir("Solicitando o relatório" + (nomePessoa == null || nomePessoa.isBlank() ? "..." : " de " + nomePessoa + "..."));
        System.out.println(">>> TOOL CHAMADA: solicitarRelatorioPessoa(nomePessoa=" + nomePessoa + ", formato=" + formato + ")");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        try {
            relatorioService.solicitarRelatorioPessoa(usuario.getId(), nomePessoa == null ? null : nomePessoa.trim(), formato);
        } catch (IllegalStateException e) {
            return "Não foi possível gerar esse relatório: " + e.getMessage();
        }
        estadoSessao.setRelatorioGerado(true);

        return "Relatório solicitado! Já abri o painel de relatórios pra você acompanhar — deve ficar pronto em cerca de 20-30 segundos.";
    }

    @Tool("Busca o texto completo do último relatório de desempenho que o usuário atual gerou. Use quando o usuário perguntar algo sobre 'o relatório que gerei', 'aquele relatório', ou pedir para comentar/explicar/aprofundar algo do relatório recém-criado.")
    public String buscarUltimoRelatorioGerado() {
        statusExecucao.definir("Recuperando seu último relatório...");
        System.out.println(">>> TOOL CHAMADA: buscarUltimoRelatorioGerado()");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarioService.buscarPorEmail(email);

        List<Map<String, Object>> resultado = jdbcTemplate.queryForList("""
            SELECT departamento, tipo, status, texto_relatorio, criado_em, COALESCE(sujeito_tipo, 'DEPARTAMENTO') AS sujeito_tipo
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
            String texto = "PESSOA".equals(ultimo.get("sujeito_tipo"))
                    ? MAPPER.readValue(json, RelatorioPessoaDTO.class).paraTextoLegivel()
                    : MAPPER.readValue(json, RelatorioEstruturadoDTO.class).paraTextoLegivel();
            System.out.println(">>> TOOL RESULTADO: relatório de " + ultimo.get("departamento") + " recuperado (" + texto.length() + " caracteres)");
            return "RELATÓRIO MAIS RECENTE (" + ultimo.get("departamento") + ", " + ultimo.get("tipo") + "):\n\n" + texto;
        } catch (Exception e) {
            return "O último relatório existe mas houve falha ao ler seu conteúdo.";
        }
    }
}
