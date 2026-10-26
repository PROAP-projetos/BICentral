package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.IndicadorInepDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.ItemIesgoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.PraticaIesgoDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Diretrizes INEP e IESGo de cada ação do PDI, lidas de resources/diretrizes/acoes-diretrizes.json
 * (gerado por gerar-diretrizes.py a partir da base do painel-rastreabilidade). Base estática e pequena,
 * por isso fica em memória em vez de numa tabela.
 */
@Service
public class DiretrizesService {

    private static final String ARQUIVO = "diretrizes/acoes-diretrizes.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Base(String versao, List<DiretrizesAcaoDTO> acoes) {}

    private final Map<String, DiretrizesAcaoDTO> porCodigo = new HashMap<>();
    private final String versao;

    // Falha na subida se o arquivo faltar ou estiver quebrado: ele vai dentro do .jar, então isso é erro
    // de build, e é melhor descobrir no deploy do que com o agente dizendo que nenhuma ação tem diretriz.
    public DiretrizesService() throws IOException {
        try (InputStream entrada = new ClassPathResource(ARQUIVO).getInputStream()) {
            Base base = new ObjectMapper().readValue(entrada, Base.class);
            for (DiretrizesAcaoDTO acao : base.acoes()) {
                porCodigo.put(acao.numeroAcao(), acao);
            }
            this.versao = base.versao();
        }
    }

    /** Aceita o código como aparece no PAT ("U 1.1.1.1") ou só o número ("1.1.1.1"). */
    public Optional<DiretrizesAcaoDTO> buscar(String codigoAcao) {
        if (codigoAcao == null) {
            return Optional.empty();
        }
        String numero = codigoAcao.trim().replaceFirst("^[A-Za-z]+\\s*", "");
        return Optional.ofNullable(porCodigo.get(numero));
    }

    public String versao() {
        return versao;
    }

    public static boolean temDiretrizes(DiretrizesAcaoDTO acao) {
        return acao != null && !(vazio(acao.inep()) && vazio(acao.iesgo()));
    }

    /** Códigos no formato que a análise cita: "INEP 1.2", "IESGo 2132". */
    public static Set<String> codigos(DiretrizesAcaoDTO acao) {
        Set<String> codigos = new LinkedHashSet<>();
        for (IndicadorInepDTO indicador : listaOuVazia(acao.inepDetalhes())) {
            codigos.add("INEP " + indicador.codigo());
        }
        for (PraticaIesgoDTO pratica : listaOuVazia(acao.iesgoDetalhes())) {
            codigos.add("IESGo " + pratica.codigo());
        }
        return codigos;
    }

    /** Diretrizes da ação com o critério de cada indicador e os itens de cada prática, para a IA comparar. */
    public static String descrever(DiretrizesAcaoDTO acao) {
        StringBuilder sb = new StringBuilder();
        sb.append("INEP: ").append(vazio(acao.inep()) ? "nenhum" : acao.inep()).append("\n");
        for (IndicadorInepDTO indicador : listaOuVazia(acao.inepDetalhes())) {
            sb.append("- ").append(indicador.codigo()).append(" ").append(indicador.indicador())
              .append(": ").append(indicador.criterio()).append("\n");
        }
        sb.append("IESGo: ").append(vazio(acao.iesgo()) ? "nenhum" : acao.iesgo()).append("\n");
        for (PraticaIesgoDTO pratica : listaOuVazia(acao.iesgoDetalhes())) {
            sb.append("- ").append(pratica.codigo()).append(":");
            for (ItemIesgoDTO item : listaOuVazia(pratica.itens())) {
                sb.append(" (").append(item.item()).append(") ").append(item.texto());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static <T> List<T> listaOuVazia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    public int totalAcoes() {
        return porCodigo.size();
    }
}
