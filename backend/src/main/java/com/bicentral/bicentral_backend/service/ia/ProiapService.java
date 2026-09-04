package com.bicentral.bicentral_backend.service.ia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.AnaliseComandoDTO;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAGDTO;
import com.bicentral.bicentral_backend.dto.ia.IntencaoDTO;
import com.bicentral.bicentral_backend.dto.ia.RespostaTextualDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelRespostaDTO;
import com.bicentral.bicentral_backend.dto.painel.PainelSpecDTO;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProiapService {

    private final AgenteProiap agenteProiap;
    private final EstadoSessao estadoSessao;
    private final EmbeddingService embeddingService;
    private final AgenteConsultaSql agenteConsultaSql;
    private final UsoIaService usoIaService;

    private static final int MAX_SUGESTOES = 3;

    private static final Map<String, List<String>> SUGESTOES_POR_FERRAMENTA = Map.of(
        "ranquearDepartamentosPorExecucaoPAT", List.of(
            "Alguma dessas ações é compartilhada entre departamentos?",
            "Quero ver a distribuição de status dessas unidades num gráfico"),
        "buscarExecucaoPATPorDepartamento", List.of("Quero um relatório completo dessa unidade"),
        "buscarDetalhamentoDesempenhoDepartamento", List.of(
            "Essas ações têm outros departamentos envolvidos?",
            "Quero ver a execução média num indicador visual"),
        "rastrearGargaloEmAcaoCompartilhada", List.of("Quero um relatório da unidade mais atrasada"),
        "contarAcoesPorDepartamentoPAT", List.of("Qual o desempenho dessas unidades no PAT?"),
        "buscarMinhasTarefas", List.of("Quais dessas estão atrasadas?"),
        "buscarTarefasPorDepartamento", List.of("Quero o relatório completo dessa unidade"),
        "compararExecucaoPDIxPAT", List.of("Quero ver essa comparação num gráfico")
    );

    private static final List<String> MENSAGENS_PAINEL_PRONTO = List.of(
        "Prontinho! Aqui está o painel. Se quiser mudar o formato (ex: pizza) ou o título, é só pedir.",
        "Pronto, montei o painel! Quer ajustar o tipo de gráfico ou o título? É só falar.",
        "Aqui está! Se não ficou do jeito que você queria — outro formato, outro título — é só pedir de novo.",
        "Painel gerado! Fica à vontade pra pedir outro formato (barra, pizza, linha) ou trocar o título.",
        "Feito! Se quiser ver de outro jeito (outro tipo de gráfico) ou mudar o título, é só me falar."
    );

    public ProiapService(AgenteProiap agenteProiap, AgenteConsultaSql agenteConsultaSql, EstadoSessao estadoSessao,
            EmbeddingService embeddingService, UsoIaService usoIaService) {
        this.agenteProiap = agenteProiap;
        this.agenteConsultaSql = agenteConsultaSql;
        this.estadoSessao = estadoSessao;
        this.embeddingService = embeddingService;
        this.usoIaService = usoIaService;
    }

    public Object processarPergunta(String perguntaUsuario, String sessaoId, boolean usuarioEhAdmin, Long usuarioId) {

        if (usoIaService.deveBloquear(usuarioId)) {
            return new RespostaTextualDTO(
                    "Esse teste atingiu o limite de uso combinado com o time. Muito obrigada por testar! 💙",
                    null, false, List.of(), null);
        }

        estadoSessao.setRelatorioGerado(false);

        System.out.println("\n================================");
        System.out.println("NOVA REQUISIÇÃO");
        System.out.println("Pergunta: " + perguntaUsuario);
        System.out.println("Sessao ID Front: " + sessaoId);
        System.out.println("Sessao Hash (Estado): " + System.identityHashCode(estadoSessao));
        System.out.println("Aguardando confirmação: " + estadoSessao.isAguardandoConfirmacaoGrafico());
        System.out.println("Tem painel pendente: " + (estadoSessao.getPainelPendente() != null));
        System.out.println("================================");

        if (estadoSessao.isAguardandoConfirmacaoGrafico()) {

            System.out.println(">>> ENTROU NO BLOCO DE CONFIRMAÇÃO");

            String classificacao = agenteProiap.classificarConfirmacao(UUID.randomUUID().toString(), perguntaUsuario)
                    .trim().toUpperCase();
            System.out.println("IA Classificou a resposta como: " + classificacao);

            if (classificacao.contains("CONFIRMAR")) {
                System.out.println(">>> USUÁRIO CONFIRMOU (IA ENTENDEU)");
                PainelSpecDTO pendente = estadoSessao.getPainelPendente();
                Long interacaoId = estadoSessao.getInteracaoIdPendente();

                String mensagemPronto = MENSAGENS_PAINEL_PRONTO.get(
                        ThreadLocalRandom.current().nextInt(MENSAGENS_PAINEL_PRONTO.size()));

                PainelRespostaDTO painelPronto = new PainelRespostaDTO(
                        pendente.skill(),
                        mensagemPronto,
                        pendente.titulo(),
                        pendente.graficos(),
                        false,
                        interacaoId);

                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setPainelPendente(null);
                estadoSessao.setInteracaoIdPendente(null);

                return painelPronto;
            }

            if (classificacao.contains("NEGAR")) {
                System.out.println(">>> USUÁRIO NEGOU (IA ENTENDEU)");
                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setPainelPendente(null);
                estadoSessao.setInteracaoIdPendente(null);

                return new RespostaTextualDTO("Tudo bem! Me diz o que você quer ver e eu busco novamente.", null, false, List.of(), null);
            }

            System.out.println(">>> USUÁRIO REFORMULOU A CONSULTA (IA ENTENDEU)");
            estadoSessao.setAguardandoConfirmacaoGrafico(false);
            estadoSessao.setPainelPendente(null);
        }

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
        String modelo = estadoSessao.getModelo() != null ? estadoSessao.getModelo() : "Llama 3 (Groq)";
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
            
            Result<String> resultado = agenteConsultaSql.responderComFerramentas(memoryId, perguntaUsuario,
                    contextoRAG.textoContexto());
            List<String> sugestoes = montarSugestoes(resultado.toolExecutions());
            Long interacaoId = usoIaService.registrarUso(usuarioId, perguntaUsuario, resultado.content(), resultado.tokenUsage());

            return new RespostaTextualDTO(resultado.content(), contextoRAG.fontes(), estadoSessao.isRelatorioGerado(), sugestoes, interacaoId);
            
        } else if (analise.intencao() == IntencaoDTO.GRAFICO) {

            Result<String> dadosResultado = agenteConsultaSql.responderComFerramentas(
                    "grafico-" + UUID.randomUUID(), perguntaUsuario, contextoRAG.textoContexto());
            Long interacaoId = usoIaService.registrarUso(usuarioId, perguntaUsuario, dadosResultado.content(), dadosResultado.tokenUsage());

            PainelSpecDTO spec = agenteProiap.gerarPainel(
                    perguntaUsuario,
                    dadosResultado.content(),
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            System.out.println(">>> NOVO PAINEL GERADO (" + spec.graficos().size() + " gráfico(s))");
            System.out.println(">>> SALVANDO COMO PENDENTE");

            estadoSessao.setPainelPendente(spec);
            estadoSessao.setAguardandoConfirmacaoGrafico(true);
            estadoSessao.setInteracaoIdPendente(interacaoId);

            List<String> sugestoes = montarSugestoes(dadosResultado.toolExecutions());
            return new RespostaTextualDTO(spec.mensagemContexto(), contextoRAG.fontes(), false, sugestoes, interacaoId);
        }

        return new RespostaTextualDTO("Desculpe, não consegui entender a intenção do seu comando.", null, false, List.of(), null);
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