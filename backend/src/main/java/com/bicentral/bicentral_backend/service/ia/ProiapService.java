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
        Map.entry("salvarPreferenciaUsuario", List.of("O que você lembra sobre mim?")),
        Map.entry("listarMinhasMemorias", List.of("Quero atualizar uma dessas preferências"))
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
            
            // Preferências que o usuário já pediu explicitamente pra lembrar (ver MemoriaTool) —
            // entram no mesmo CONTEXTO do RAG, na frente, pra IA já ver o que existe antes de
            // decidir se uma nova instrução é inédita ou substitui uma memória anterior.
            String contextoComMemoria = memoriaUsuarioService.montarBlocoMemoria(usuarioId) + contextoRAG.textoContexto();

            Result<String> resultado = agenteConsultaSql.responderComFerramentas(memoryId, perguntaUsuario,
                    contextoComMemoria);
            List<String> sugestoes = montarSugestoes(resultado.toolExecutions());
            Long interacaoId = usoIaService.registrarUso(usuarioId, sessaoId, perguntaUsuario, resultado.content(), resultado.tokenUsage());

            chatHistoricoService.salvarUser(sessaoId, usuarioId, perguntaUsuario);
            chatHistoricoService.salvarBot(sessaoId, usuarioId, resultado.content(), null, contextoRAG.fontes(), sugestoes, interacaoId);
            return new RespostaTextualDTO(resultado.content(), contextoRAG.fontes(), estadoSessao.isRelatorioGerado(), sugestoes, interacaoId, estadoSessao.isMemoriaAtualizada());
            
        } else if (analise.intencao() == IntencaoDTO.GRAFICO) {

            Result<String> dadosResultado = agenteConsultaSql.responderComFerramentas(
                    "grafico-" + UUID.randomUUID(), perguntaUsuario, contextoRAG.textoContexto());
            Long interacaoId = usoIaService.registrarUso(usuarioId, sessaoId, perguntaUsuario, dadosResultado.content(), dadosResultado.tokenUsage());

            PainelSpecDTO spec = agenteProiap.gerarPainel(
                    perguntaUsuario,
                    dadosResultado.content(),
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            if (!painelTemDados(spec)) {
                // Sem dado real pra mostrar, não tem painel nenhum pra exibir — responde como
                // texto direto (igual RESPOSTA) explicando o que faltou.
                System.out.println(">>> PAINEL SEM DADOS — respondendo como texto");
                List<String> sugestoesVazio = montarSugestoes(dadosResultado.toolExecutions());
                chatHistoricoService.salvarUser(sessaoId, usuarioId, perguntaUsuario);
                chatHistoricoService.salvarBot(sessaoId, usuarioId, spec.mensagemContexto(), null, contextoRAG.fontes(), sugestoesVazio, interacaoId);
                return new RespostaTextualDTO(spec.mensagemContexto(), contextoRAG.fontes(), false, sugestoesVazio, interacaoId, false);
            }

            // Tem dado real — mostra o painel direto, sem pausar pra perguntar "quer ver?"
            // (isso já foi removido: só existia um caminho de confirmação, nunca uma recusa
            // real com efeito, então era um passo a mais sem ganho).
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
            List<String> candidatas = SUGESTOES_POR_FERRAMENTA.get(execucao.request().name());
            if (candidatas != null) {
                sugestoes.addAll(candidatas);
            }
            if (sugestoes.size() >= MAX_SUGESTOES) break;
        }
        return sugestoes.stream().limit(MAX_SUGESTOES).toList();
    }
}