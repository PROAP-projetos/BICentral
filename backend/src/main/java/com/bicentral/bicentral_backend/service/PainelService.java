package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PainelService {

    private static final Logger logger = LoggerFactory.getLogger(PainelService.class);

    private static final String PREFIXO_POWER_BI = "https://app.powerbi.com/view?r=";

    private final PainelRepository painelRepository;
    private final UsuarioRepository usuarioRepository;
    private final PowerBIScraperService scraperService;

    public PainelService(
            PainelRepository painelRepository,
            UsuarioRepository usuarioRepository,
            PowerBIScraperService scraperService
    ) {
        this.painelRepository = painelRepository;
        this.usuarioRepository = usuarioRepository;
        this.scraperService = scraperService;
    }

    // -------------------------
    // Helpers
    // -------------------------

    private Usuario getUsuarioLogado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new AutenticacaoException("Usuário não autenticado");
        }

        String email = authentication.getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AutenticacaoException("Usuário não encontrado: " + email));
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private void validarPrefixoPowerBi(String link) {
        if (link == null) return;
        if (!link.startsWith(PREFIXO_POWER_BI)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Link inválido. O link deve começar com: " + PREFIXO_POWER_BI
            );
        }
    }

    private PainelDTO toDTO(Painel painel) {
        PainelDTO dto = new PainelDTO();
        dto.setId(painel.getId());
        dto.setNome(painel.getNome());
        dto.setLinkPowerBi(painel.getLinkPowerBi());
        dto.setImagemCapaUrl(painel.getImagemCapaUrl());
        dto.setStatusCaptura(painel.getStatusCaptura());
        return dto;
    }

    // -------------------------
    // CREATE
    // -------------------------

    @Transactional
    public PainelDTO criarPainel(Painel painel) {
        Usuario usuario = getUsuarioLogado();

        String linkLimpo = trimOrNull(painel.getLinkPowerBi());
        validarPrefixoPowerBi(linkLimpo);

        boolean duplicado = painelRepository.existsByLinkPowerBiAndUsuario_Id(linkLimpo, usuario.getId());
        if (duplicado) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Você já possui este painel cadastrado na sua lista."
            );
        }

        painel.setUsuario(usuario);
        painel.setLinkPowerBi(linkLimpo);
        painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
        painel.setImagemCapaUrl(null);

        Painel painelSalvo = painelRepository.save(painel);
        logger.info("Painel criado com sucesso ID: {}", painelSalvo.getId());

        return toDTO(painelSalvo);
    }

    // -------------------------
    // READ (LIST) - MEUS PAINÉIS
    // -------------------------

    public List<PainelDTO> listarMeusPaineis() {
        Usuario usuario = getUsuarioLogado();

        return painelRepository.findAllByUsuario_Id(usuario.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // -------------------------
    // READ - by ID (só se for do usuário)
    // -------------------------

    public PainelDTO buscarPorId(Long id) {
        Usuario usuario = getUsuarioLogado();

        Painel painel = painelRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado."));

        return toDTO(painel);
    }

    // -------------------------
    // UPDATE
    // -------------------------

    @Transactional
    public PainelDTO atualizarPainel(Long id, PainelDTO dto) {
        Usuario usuario = getUsuarioLogado();

        Painel painel = painelRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado."));

        boolean linkMudou = false;

        // 1) Nome: altera sem mexer em scraping
        String nomeNovo = trimOrNull(dto.getNome());
        if (nomeNovo != null && !nomeNovo.equals(trimOrNull(painel.getNome()))) {
            painel.setNome(nomeNovo);
        }

        // 2) Link: só valida/processa se veio no payload
        if (dto.getLinkPowerBi() != null) {
            String novoLink = trimOrNull(dto.getLinkPowerBi());
            validarPrefixoPowerBi(novoLink);

            // normalize também o que vem do banco (pra não disparar scraping por espaço)
            String linkAtual = trimOrNull(painel.getLinkPowerBi());

            if (linkAtual == null || !linkAtual.equals(novoLink)) {
                boolean duplicado = painelRepository.existsByLinkPowerBiAndUsuario_IdAndIdNot(
                        novoLink, usuario.getId(), id
                );
                if (duplicado) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Você já possui este painel cadastrado na sua lista."
                    );
                }

                painel.setLinkPowerBi(novoLink);
                linkMudou = true;

                // ✅ se link mudou, reseta scraping
                painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
                painel.setImagemCapaUrl(null);
            }
        }

        Painel salvo = painelRepository.save(painel);

        // ✅ só dispara scraping se link mudou
        if (linkMudou) {
            try {
                scraperService.capturaCapaAsync(id);
            } catch (Exception e) {
                logger.error("Erro ao iniciar captura assíncrona após update para ID: {}", id, e);
            }
        }

        return toDTO(salvo);
    }

    // -------------------------
    // DELETE
    // -------------------------

    @Transactional
    public void deletarPainel(Long id) {
        Usuario usuario = getUsuarioLogado();

        Painel painel = painelRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado."));

        painelRepository.delete(painel);
        logger.info("Painel ID: {} deletado (usuarioId={}).", id, usuario.getId());
    }
}
