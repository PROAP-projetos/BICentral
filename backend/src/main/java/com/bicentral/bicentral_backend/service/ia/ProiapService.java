package com.bicentral.bicentral_backend.service.ia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.AnaliseComandoDTO;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAGDTO;
import com.bicentral.bicentral_backend.dto.ia.IntencaoDTO;
import com.bicentral.bicentral_backend.dto.ia.RespostaTextualDTO;
import com.bicentral.bicentral_backend.dto.painel.GraficoSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelRespostaDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;
import com.bicentral.bicentral_backend.dto.painel.SerieGraficoDTO;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.LinkedHashSet;
import java.util.Map;

@Service
public class ProiapService {

    private final AgenteProiap agenteProiap;
    private final EstadoSessao estadoSessao;
    private final EmbeddingService embeddingService;
    private final AgenteConsultaSql agenteConsultaSql;
    private final UsoIaService usoIaService;
    private final ChatHistoricoService chatHistoricoService;
    private final MemoriaUsuarioService memoriaUsuarioService;

    private static final int MAX_SUGESTOES = 3;

    // Prefixo fixo que RelatorioContextoTool devolve quando a geração falha (marcador sem
    // match, pessoa sem permissão etc.) — sem isso, chips tipo "Quero também em Excel"
    // apareciam mesmo quando o relatório não foi gerado, sem sentido nenhum pro contexto.
    private static final String PREFIXO_FALHA_RELATORIO = "Não foi possível gerar esse relatório:";

    private static final Map<String, List<String>> SUGESTOES_POR_FERRAMENTA = Map.ofEntries(
        Map.entry("ranquearDepartamentosPorExecucaoPAT", List.of(
            "Alguma dessas ações é compartilhada entre departamentos?",
            "Quero ver a distribuição de status dessas unidades num gráfico")),
        Map.entry("buscarExecucaoPATPorDepartamento", List.of("Quero um relatório completo dessa unidade")),
        Map.entry("buscarDetalhamentoDesempenhoDepartamento", List.of(
            "Essas ações têm outros departamentos envolvidos?",
            "Quero ver a execução média num indicador visual")),
        Map.entry("rastrearGargaloEmAcaoCompartilhada", List.of("Quero um relatório da unidade mais atrasada")),
        Map.entry("contarAcoesPorDepartamentoPAT", List.of("Qual o desempenho dessas unidades no PAT?")),
        Map.entry("buscarMinhasTarefas", List.of("Quais dessas estão atrasadas?", "Mostra todas as minhas tarefas")),
        Map.entry("buscarTarefas", List.of(
            "Quero um relatório completo dessa unidade",
            "E as que estão atrasadas?",
            "Mostra todas")),
        Map.entry("contarAcoesUnicasPAT", List.of("Qual departamento tem mais ações no PAT?")),
        Map.entry("buscarAcoesPorMarcador", List.of("Quero um relatório completo desse departamento")),
        Map.entry("contarTarefasPorDepartamento", List.of("Qual departamento tem mais tarefas atrasadas?")),
        Map.entry("contarTarefasPorAcao", List.of("Quero ver as tarefas dessa ação")),
        Map.entry("salvarPreferenciaUsuario", List.of("O que você lembra sobre mim?")),
        Map.entry("listarMinhasMemorias", List.of("Quero atualizar uma dessas preferências")),
        Map.entry("solicitarGeracaoRelatorio", List.of(
            "Quero também em Excel",
            "Mostra as tarefas de cada ação",
            "Quero ver o nome da ação em vez do código")),
        Map.entry("solicitarRelatorioPessoa", List.of(
            "Quero também em Excel",
            "E o relatório do departamento inteiro?"))
    );

    public ProiapService(AgenteProiap agenteProiap, AgenteConsultaSql agenteConsultaSql, EstadoSessao estadoSessao,
            EmbeddingService embeddingService, UsoIaService usoIaService, ChatHistoricoService chatHistoricoService,
            MemoriaUsuarioService memoriaUsuarioService) {
        this.agenteProiap = agenteProiap;
        this.agenteConsultaSql = agenteConsultaSql;
        this.estadoSessao = estadoSessao;
        this.embeddingService = embeddingService;
        this.usoIaService = usoIaService;
        this.chatHistoricoService = chatHistoricoService;
        this.memoriaUsuarioService = memoriaUsuarioService;
    }

    public Object processarPergunta(String perguntaUsuario, String sessaoId, boolean usuarioEhAdmin, Long usuarioId) {

        if (usoIaService.deveBloquear(usuarioId)) {
            return new RespostaTextualDTO(
                    "Esse teste atingiu o limite de uso combinado com o time. Muito obrigada por testar! 💙",
                    null, false, List.of(), null, false);
        }

        estadoSessao.setRelatorioGerado(false);
        estadoSessao.setMemoriaAtualizada(false);

        System.out.println("\n================================");
        System.out.println("NOVA REQUISIÇÃO");
        System.out.println("Pergunta: " + perguntaUsuario);
        System.out.println("Sessao ID Front: " + sessaoId);
        System.out.println("Sessao Hash (Estado): " + System.identityHashCode(estadoSessao));
        System.out.println("================================");

        AnaliseComandoDTO analise = agenteProiap.analisarComando(UUID.randomUUID().toString(), perguntaUsuario);

        System.out.println("Intenção detectada: " + analise.intencao());

        if (analise.tipoGrafico() != null)
            estadoSessao.setTipoGrafico(analise.tipoGrafico());

        if (analise.indicador() != null)
            estadoSessao.setIndicador(analise.indicador());

        if (analise.curso() != null)
            estadoSessao.setCurso(analise.curso());

        if (analise.ano() != null) {
            try {
                estadoSessao.setAno(Integer.parseInt(analise.ano().replaceAll("[^0-9]", "")));
            } catch (NumberFormatException e) {
                System.err.println("WARN - Ano inválido: " + analise.ano());
            }
        }

        Long equipeDaSessao = estadoSessao.getEquipeId() != null ? estadoSessao.getEquipeId() : 1L;
        String modelo = estadoSessao.getModelo() != null ? estadoSessao.getModelo() : "Gemini 2.5 Flash";
        String termoDeBusca = perguntaUsuario;

        if (analise.intencao() == IntencaoDTO.GRAFICO) {
            String indicadorParaBusca = analise.indicador() != null ? analise.indicador() : estadoSessao.getIndicador();
            if (indicadorParaBusca != null && !indicadorParaBusca.equals("Todos")) {
                termoDeBusca = indicadorParaBusca + " " + perguntaUsuario;
            }
        }

        ContextoRAGDTO contextoRAG = usuarioEhAdmin
                   ? embeddingService.buscarContextoSemelhante(termoDeBusca, equipeDaSessao, modelo)
                   : new ContextoRAGDTO("", List.of());
                   
        System.out.println("DEBUG - Termo usado na busca: " + termoDeBusca);
        System.out.println("DEBUG - Fontes encontradas: " + contextoRAG.fontes());

        if (analise.intencao() == IntencaoDTO.RESPOSTA) {
            
            String memoryId = (sessaoId != null && !sessaoId.isBlank()) 
                    ? sessaoId 
                    : "sessao-fallback-" + System.identityHashCode(estadoSessao);
            
            // Memória do usuário (ver MemoriaTool) entra na frente do contexto de RAG.
            String contextoComMemoria = memoriaUsuarioService.montarBlocoMemoria(usuarioId) + contextoRAG.textoContexto();

            Result<String> resultado = agenteConsultaSql.responderComFerramentas(memoryId, perguntaUsuario,
                    contextoComMemoria);
            String conteudo = tratarRespostaTruncada(resultado);
            List<String> sugestoes = montarSugestoes(resultado.toolExecutions());
            Long interacaoId = usoIaService.registrarUso(usuarioId, sessaoId, perguntaUsuario, conteudo, resultado.tokenUsage());

            chatHistoricoService.salvarUser(sessaoId, usuarioId, perguntaUsuario);
            chatHistoricoService.salvarBot(sessaoId, usuarioId, conteudo, null, contextoRAG.fontes(), sugestoes, interacaoId);
            return new RespostaTextualDTO(conteudo, contextoRAG.fontes(), estadoSessao.isRelatorioGerado(), sugestoes, interacaoId, estadoSessao.isMemoriaAtualizada());

        } else if (analise.intencao() == IntencaoDTO.GRAFICO) {

            Result<String> dadosResultado = agenteConsultaSql.responderComFerramentas(
                    "grafico-" + UUID.randomUUID(), perguntaUsuario, contextoRAG.textoContexto());
            String dadosConteudo = tratarRespostaTruncada(dadosResultado);
            Long interacaoId = usoIaService.registrarUso(usuarioId, sessaoId, perguntaUsuario, dadosConteudo, dadosResultado.tokenUsage());

            PainelSpecDTO spec = agenteProiap.gerarPainel(
                    perguntaUsuario,
                    dadosConteudo,
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            if (!painelTemDados(spec)) {
                // Sem dado real, não tem painel pra exibir — responde como texto explicando o que faltou.
                System.out.println(">>> PAINEL SEM DADOS — respondendo como texto");
                List<String> sugestoesVazio = montarSugestoes(dadosResultado.toolExecutions());
                chatHistoricoService.salvarUser(sessaoId, usuarioId, perguntaUsuario);
                chatHistoricoService.salvarBot(sessaoId, usuarioId, spec.mensagemContexto(), null, contextoRAG.fontes(), sugestoesVazio, interacaoId);
                return new RespostaTextualDTO(spec.mensagemContexto(), contextoRAG.fontes(), false, sugestoesVazio, interacaoId, false);
            }

            // Tem dado real — mostra o painel direto, sem etapa de confirmação.
            System.out.println(">>> NOVO PAINEL GERADO (" + spec.graficos().size() + " gráfico(s))");

            PainelRespostaDTO painelPronto = new PainelRespostaDTO(
                    spec.skill(),
                    spec.mensagemContexto(),
                    spec.titulo(),
                    spec.graficos(),
                    false,
                    interacaoId);

            chatHistoricoService.salvarUser(sessaoId, usuarioId, perguntaUsuario);
            chatHistoricoService.salvarBot(sessaoId, usuarioId, spec.mensagemContexto(), painelPronto, null, null, interacaoId);
            return painelPronto;
        }

        return new RespostaTextualDTO("Desculpe, não consegui entender a intenção do seu comando.", null, false, List.of(), null, false);
    }

    private static final String AVISO_RESPOSTA_CORTADA =
            "\n\n⚠️ A resposta foi cortada por ficar muito extensa. Peça uma parte específica (ex: só as atrasadas, só os primeiros 10 itens) para ver o restante.";

    private String tratarRespostaTruncada(Result<String> resultado) {
        String conteudo = resultado.content();
        if (resultado.finishReason() != FinishReason.LENGTH) {
            return conteudo;
        }
        System.out.println(">>> AVISO: resposta cortada por limite de tokens (finishReason=LENGTH)");
        int ultimaQuebra = conteudo.lastIndexOf('\n');
        String conteudoLimpo = ultimaQuebra > 0 ? conteudo.substring(0, ultimaQuebra) : conteudo;
        return conteudoLimpo + AVISO_RESPOSTA_CORTADA;
    }

    /** true se pelo menos um gráfico do painel tem pelo menos um valor real — critério pra decidir se vale a pena oferecer confirmação de exibição, ou se é melhor só avisar que faltou dado. */
    private boolean painelTemDados(PainelSpecDTO spec) {
        if (spec.graficos() == null) return false;
        for (GraficoSpecDTO grafico : spec.graficos()) {
            if (grafico.series() == null) continue;
            for (SerieGraficoDTO serie : grafico.series()) {
                if (serie.valores() != null && !serie.valores().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> montarSugestoes(List<ToolExecution> execucoes) {
        if (execucoes == null || execucoes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sugestoes = new LinkedHashSet<>();
        for (ToolExecution execucao : execucoes) {
            String resultado = execucao.result();
            if (resultado != null && resultado.startsWith(PREFIXO_FALHA_RELATORIO)) {
                continue;
            }
            List<String> candidatas = SUGESTOES_POR_FERRAMENTA.get(execucao.request().name());
            if (candidatas != null) {
                sugestoes.addAll(candidatas);
            }
            if (sugestoes.size() >= MAX_SUGESTOES) break;
        }
        return sugestoes.stream().limit(MAX_SUGESTOES).toList();
    }
}