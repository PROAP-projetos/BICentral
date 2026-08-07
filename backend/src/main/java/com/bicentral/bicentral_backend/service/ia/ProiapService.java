package com.bicentral.bicentral_backend.service.ia;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bicentral.bicentral_backend.dto.ia.AnaliseComando;
import com.bicentral.bicentral_backend.dto.ia.ContextoRAG;
import com.bicentral.bicentral_backend.dto.ia.Intencao;
import com.bicentral.bicentral_backend.dto.ia.RespostaTextual;
import com.bicentral.bicentral_backend.dto.painel.GraficoSpec;
import com.bicentral.bicentral_backend.state.EstadoSessao;

@Service
public class ProiapService {

    private final AgenteProiap agenteProiap;
    private final EstadoSessao estadoSessao;
    private final EmbeddingService embeddingService;
    private final AgenteConsultaSql agenteConsultaSql;

    public ProiapService(AgenteProiap agenteProiap, AgenteConsultaSql agenteConsultaSql, EstadoSessao estadoSessao,
            EmbeddingService embeddingService) {
        this.agenteProiap = agenteProiap;
        this.agenteConsultaSql = agenteConsultaSql;
        this.estadoSessao = estadoSessao;
        this.embeddingService = embeddingService;
    }

    public Object processarPergunta(String perguntaUsuario, String sessaoId) {

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
                GraficoSpec pendente = estadoSessao.getGraficoPendente();

                GraficoSpec graficoPronto = new GraficoSpec(
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

                return new RespostaTextual("Tudo bem! Me diz o que você quer ver e eu busco novamente.", null, false);
            }

            System.out.println(">>> USUÁRIO REFORMULOU A CONSULTA (IA ENTENDEU)");
            estadoSessao.setAguardandoConfirmacaoGrafico(false);
            estadoSessao.setGraficoPendente(null);
        }

        AnaliseComando analise = agenteProiap.analisarComando(UUID.randomUUID().toString(), perguntaUsuario);

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

        if (analise.intencao() == Intencao.GRAFICO) {
            String indicadorParaBusca = analise.indicador() != null ? analise.indicador() : estadoSessao.getIndicador();
            if (indicadorParaBusca != null && !indicadorParaBusca.equals("Todos")) {
                termoDeBusca = indicadorParaBusca + " " + perguntaUsuario;
            }
        }

        // ================================================================
        // BUSCA O CONTEXTO E AS FONTES
        // ================================================================
        ContextoRAG contextoRAG = embeddingService.buscarContextoSemelhante(termoDeBusca, equipeDaSessao, modelo);

        System.out.println("DEBUG - Termo usado na busca: " + termoDeBusca);
        System.out.println("DEBUG - Fontes encontradas: " + contextoRAG.fontes());

        // ================================================================
        // ROTEAMENTO
        // ================================================================
        if (analise.intencao() == Intencao.RESPOSTA) {
            
            String memoryId = (sessaoId != null && !sessaoId.isBlank()) 
                    ? sessaoId 
                    : "sessao-fallback-" + System.identityHashCode(estadoSessao);
            
            String respostaTexto = agenteConsultaSql.responderComFerramentas(memoryId, perguntaUsuario,
                    contextoRAG.textoContexto());

            return new RespostaTextual(respostaTexto, contextoRAG.fontes(), estadoSessao.isRelatorioGerado());
            
        } else if (analise.intencao() == Intencao.GRAFICO) {

            GraficoSpec spec = agenteProiap.gerarGrafico(
                    perguntaUsuario,
                    contextoRAG.textoContexto(),
                    estadoSessao.getIndicador(),
                    estadoSessao.getTipoGrafico());

            System.out.println(">>> NOVO GRÁFICO GERADO");
            System.out.println(">>> SALVANDO COMO PENDENTE");

            estadoSessao.setGraficoPendente(spec);
            estadoSessao.setAguardandoConfirmacaoGrafico(true);

            return new RespostaTextual(spec.mensagemContexto(), contextoRAG.fontes(), false);
        }

        return new RespostaTextual("Desculpe, não consegui entender a intenção do seu comando.", null, false);
    }
}