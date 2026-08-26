package com.bicentral.bicentral_backend.service.ia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.AnaliseComandoDTO;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAGDTO;
import com.bicentral.bicentral_backend.dto.ia.IntencaoDTO;
import com.bicentral.bicentral_backend.dto.ia.RespostaTextualDTO;
import com.bicentral.bicentral_backend.dto.painel.GraficoSpecDTO;
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

    private static final int MAX_SUGESTOES = 3;

    private static final Map<String, String> SUGESTOES_POR_FERRAMENTA = Map.of(
        "ranquearDepartamentosPorExecucaoPAT", "Alguma dessas ações é compartilhada entre departamentos?",
        "buscarExecucaoPATPorDepartamento", "Quero um relatório completo dessa unidade",
        "buscarDetalhamentoDesempenhoDepartamento", "Essas ações têm outros departamentos envolvidos?",
        "rastrearGargaloEmAcaoCompartilhada", "Quero um relatório da unidade mais atrasada",
        "contarAcoesPorDepartamentoPAT", "Qual o desempenho dessas unidades no PAT?",
        "buscarMinhasTarefas", "Quais dessas estão atrasadas?",
        "buscarTarefasPorDepartamento", "Quero o relatório completo dessa unidade"
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
        System.out.println("Tem gráfico pendente: " + (estadoSessao.getGraficoPendente() != null));
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
                GraficoSpecDTO pendente = estadoSessao.getGraficoPendente();

                GraficoSpecDTO graficoPronto = new GraficoSpecDTO(
                        "Prontinho! Aqui está o gráfico. Se quiser mudar o formato (ex: pizza) ou o título, é só pedir.",
                        pendente.skill(),
                        pendente.titulo(),
                        pendente.tipo(),
                        pendente.eixoX(),
                        pendente.series(),
                        false);

                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setGraficoPendente(null);

                return graficoPronto;
            }

            if (classificacao.contains("NEGAR")) {
                System.out.println(">>> USUÁRIO NEGOU (IA ENTENDEU)");
                estadoSessao.setAguardandoConfirmacaoGrafico(false);
                estadoSessao.setGraficoPendente(null);

                return new RespostaTextualDTO("Tudo bem! Me diz o que você quer ver e eu busco novamente.", null, false, List.of());
            }

            System.out.println(">>> USUÁRIO REFORMULOU A CONSULTA (IA ENTENDEU)");
            estadoSessao.setAguardandoConfirmacaoGrafico(false);
            estadoSessao.setGraficoPendente(null);
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

            GraficoSpecDTO spec = agenteProiap.gerarGrafico(
                    perguntaUsuario,
                    contextoRAG.textoContexto(),
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            System.out.println(">>> NOVO GRÁFICO GERADO");
            System.out.println(">>> SALVANDO COMO PENDENTE");

            estadoSessao.setGraficoPendente(spec);
            estadoSessao.setAguardandoConfirmacaoGrafico(true);

            return new RespostaTextualDTO(spec.mensagemContexto(), contextoRAG.fontes(), false, List.of());
        }

        return new RespostaTextualDTO("Desculpe, não consegui entender a intenção do seu comando.", null, false, List.of());
    }

    private List<String> montarSugestoes(List<ToolExecution> execucoes) {
        if (execucoes == null || execucoes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sugestoes = new LinkedHashSet<>();
        for (ToolExecution execucao : execucoes) {
            String sugestao = SUGESTOES_POR_FERRAMENTA.get(execucao.request().name());
            if (sugestao != null) {
                sugestoes.add(sugestao);
            }
            if (sugestoes.size() >= MAX_SUGESTOES) break;
        }
        return List.copyOf(sugestoes);
    }
}