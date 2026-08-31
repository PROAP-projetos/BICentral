package com.bicentral.bicentral_backend.service.ia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.AnaliseComandoDTO;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAGDTO;
import com.bicentral.bicentral_backend.dto.ia.IntencaoDTO;
import com.bicentral.bicentral_backend.dto.ia.RespostaTextualDTO;
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

    private static final int MAX_SUGESTOES = 3;

    // Uma ferramenta pode render mais de uma sugestão candidata (ex: além do follow-up de sempre,
    // uma sugestão nova pra descobrir os tipos de gráfico gauge/combo/empilhado, que ninguém
    // adivinha sozinho que existem).
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

    // Mensagem depois de o painel ser confirmado — string fixa em Java, não gerada pela IA, então
    // sem essa lista sempre sairia idêntica. Sorteia uma a cada confirmação pra variar.
    private static final List<String> MENSAGENS_PAINEL_PRONTO = List.of(
        "Prontinho! Aqui está o painel. Se quiser mudar o formato (ex: pizza) ou o título, é só pedir.",
        "Pronto, montei o painel! Quer ajustar o tipo de gráfico ou o título? É só falar.",
        "Aqui está! Se não ficou do jeito que você queria — outro formato, outro título — é só pedir de novo.",
        "Painel gerado! Fica à vontade pra pedir outro formato (barra, pizza, linha) ou trocar o título.",
        "Feito! Se quiser ver de outro jeito (outro tipo de gráfico) ou mudar o título, é só me falar."
    );

    public ProiapService(AgenteProiap agenteProiap, AgenteConsultaSql agenteConsultaSql, EstadoSessao estadoSessao,
            EmbeddingService embeddingService) {
        this.agenteProiap = agenteProiap;
        this.agenteConsultaSql = agenteConsultaSql;
        this.estadoSessao = estadoSessao;
        this.embeddingService = embeddingService;
    }

    public Object processarPergunta(String perguntaUsuario, String sessaoId, boolean usuarioEhAdmin) {

        // Limpa qualquer sinal de relatório de uma pergunta anterior antes de processar esta.
        estadoSessao.setRelatorioGerado(false);

        System.out.println("\n================================");
        System.out.println("NOVA REQUISIÇÃO");
        System.out.println("Pergunta: " + perguntaUsuario);
        System.out.println("Sessao ID Front: " + sessaoId); // Logando o ID que veio do Angular
        System.out.println("Sessao Hash (Estado): " + System.identityHashCode(estadoSessao));
        System.out.println("Aguardando confirmação: " + estadoSessao.isAguardandoConfirmacaoGrafico());
        System.out.println("Tem painel pendente: " + (estadoSessao.getPainelPendente() != null));
        System.out.println("================================");

        // ================================================================
        // INTERCEPTA CONFIRMAÇÃO
        // ================================================================
        if (estadoSessao.isAguardandoConfirmacaoGrafico()) {

            System.out.println(">>> ENTROU NO BLOCO DE CONFIRMAÇÃO");

            // Usa memória descartável (UUID) - MANTIDO
            String classificacao = agenteProiap.classificarConfirmacao(UUID.randomUUID().toString(), perguntaUsuario)
                    .trim().toUpperCase();
            System.out.println("IA Classificou a resposta como: " + classificacao);

            if (classificacao.contains("CONFIRMAR")) {
                System.out.println(">>> USUÁRIO CONFIRMOU (IA ENTENDEU)");
                PainelSpecDTO pendente = estadoSessao.getPainelPendente();

                String mensagemPronto = MENSAGENS_PAINEL_PRONTO.get(
                        ThreadLocalRandom.current().nextInt(MENSAGENS_PAINEL_PRONTO.size()));

                PainelSpecDTO painelPronto = new PainelSpecDTO(
                        pendente.skill(),
                        mensagemPronto,
                        pendente.titulo(),
                        pendente.graficos(),
                        false);

                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setPainelPendente(null);

                return painelPronto;
            }

            if (classificacao.contains("NEGAR")) {
                System.out.println(">>> USUÁRIO NEGOU (IA ENTENDEU)");
                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setPainelPendente(null);

                return new RespostaTextualDTO("Tudo bem! Me diz o que você quer ver e eu busco novamente.", null, false, List.of());
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

        // ================================================================
        // BUSCA O CONTEXTO E AS FONTES
        // ================================================================
        ContextoRAGDTO contextoRAG = usuarioEhAdmin
                   ? embeddingService.buscarContextoSemelhante(termoDeBusca, equipeDaSessao, modelo)
                   : new ContextoRAGDTO("", List.of());
                   
        System.out.println("DEBUG - Termo usado na busca: " + termoDeBusca);
        System.out.println("DEBUG - Fontes encontradas: " + contextoRAG.fontes());

        // ================================================================
        // ROTEAMENTO
        // ================================================================
        if (analise.intencao() == IntencaoDTO.RESPOSTA) {
            
            String memoryId = (sessaoId != null && !sessaoId.isBlank()) 
                    ? sessaoId 
                    : "sessao-fallback-" + System.identityHashCode(estadoSessao);
            
            Result<String> resultado = agenteConsultaSql.responderComFerramentas(memoryId, perguntaUsuario,
                    contextoRAG.textoContexto());
            List<String> sugestoes = montarSugestoes(resultado.toolExecutions());

            return new RespostaTextualDTO(resultado.content(), contextoRAG.fontes(), estadoSessao.isRelatorioGerado(), sugestoes);
            
        } else if (analise.intencao() == IntencaoDTO.GRAFICO) {

            // O painel não pode depender só do contexto de busca semântica em documentos (contextoRAG)
            // pra números de execução do PAT — isso quase nunca está indexado lá. Busca os dados reais
            // com as MESMAS ferramentas que a resposta textual usa (ranking, execução por departamento,
            // tarefas etc.), com memória descartável pra não misturar essa busca interna com a conversa
            // real do usuário no AgenteConsultaSql.
            Result<String> dadosResultado = agenteConsultaSql.responderComFerramentas(
                    "grafico-" + UUID.randomUUID(), perguntaUsuario, contextoRAG.textoContexto());

            PainelSpecDTO spec = agenteProiap.gerarPainel(
                    perguntaUsuario,
                    dadosResultado.content(),
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            System.out.println(">>> NOVO PAINEL GERADO (" + spec.graficos().size() + " gráfico(s))");
            System.out.println(">>> SALVANDO COMO PENDENTE");

            estadoSessao.setPainelPendente(spec);
            estadoSessao.setAguardandoConfirmacaoGrafico(true);

            List<String> sugestoes = montarSugestoes(dadosResultado.toolExecutions());
            return new RespostaTextualDTO(spec.mensagemContexto(), contextoRAG.fontes(), false, sugestoes);
        }

        return new RespostaTextualDTO("Desculpe, não consegui entender a intenção do seu comando.", null, false, List.of());
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