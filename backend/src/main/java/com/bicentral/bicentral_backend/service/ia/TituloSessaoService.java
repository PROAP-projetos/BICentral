package com.bicentral.bicentral_backend.service.ia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TituloSessaoService {

    private static final int MAX_CARACTERES_TITULO = 40;

    private final AgenteProiap agenteProiap;
    private final JdbcTemplate jdbcTemplate;

    public TituloSessaoService(AgenteProiap agenteProiap, JdbcTemplate jdbcTemplate) {
        this.agenteProiap = agenteProiap;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Bean separado do ChatHistoricoService de propósito: @Async só funciona quando a chamada
    // passa pelo proxy do Spring, o que exige vir de OUTRO bean (ver RelatorioService.self para
    // o mesmo problema resolvido de outro jeito). Chamado só na primeira mensagem de uma sessão
    // nova — não atrasa a resposta do chat, o título troca sozinho na sidebar logo em seguida.
    @Async
    public void gerarEAtualizarTitulo(String sessaoId, String primeiraMensagem) {
        try {
            String titulo = agenteProiap.gerarTituloSessao(primeiraMensagem);
            if (titulo == null || titulo.isBlank()) {
                return;
            }
            String tituloLimpo = titulo.trim();
            if (tituloLimpo.length() > MAX_CARACTERES_TITULO) {
                tituloLimpo = tituloLimpo.substring(0, MAX_CARACTERES_TITULO);
            }
            jdbcTemplate.update("UPDATE chat_sessao SET titulo = ? WHERE id = ?", tituloLimpo, sessaoId);
        } catch (Exception e) {
            System.err.println(">>> AVISO: falha ao gerar título da sessão " + sessaoId + ": " + e.getMessage());
        }
    }
}
