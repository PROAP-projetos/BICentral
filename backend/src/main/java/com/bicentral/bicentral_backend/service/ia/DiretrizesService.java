package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public int totalAcoes() {
        return porCodigo.size();
    }
}
