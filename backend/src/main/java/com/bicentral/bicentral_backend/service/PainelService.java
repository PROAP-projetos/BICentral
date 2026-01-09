package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PainelService {

    private static final Logger logger = LoggerFactory.getLogger(PainelService.class);

    @Autowired
    private PainelRepository painelRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Cria um novo painel e retorna o DTO preenchido para evitar valores null no Postman.
     */
    @Transactional
    public PainelDTO criarPainel(Painel painel) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null || authentication.getName().equals("anonymousUser")) {
            throw new AutenticacaoException("Usuário não autenticado");
        }

        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AutenticacaoException("Usuário não encontrado: " + email));

        // Normalização do Link
        String linkLimpo = painel.getLinkPowerBi().trim();
        painel.setLinkPowerBi(linkLimpo);

        // Verificação de Unicidade (Regra de Negócio)
        Optional<Painel> painelExistente = painelRepository.findByLinkPowerBi(linkLimpo);
        if (painelExistente.isPresent()) {
            logger.warn("Tentativa de duplicata: O link {} já existe no sistema", linkLimpo);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já possui este painel cadastrado na sua lista.");
        }

        painel.setUsuario(usuario);
        painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
        painel.setImagemCapaUrl(null);

        Painel painelSalvo = painelRepository.save(painel);
        logger.info("Painel criado com sucesso ID: {}", painelSalvo.getId());

        // Retorna o DTO mapeado corretamente
        return converterParaDTO(painelSalvo);
    }

    /**
     * Lista todos os painéis convertendo-os para DTO.
     */
    public List<PainelDTO> listarTodos() {
        return painelRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * O SEGREDO: Método de mapeamento manual para garantir que nenhum campo saia null.
     * Isso substitui o construtor quebrado do DTO.
     */
    private PainelDTO converterParaDTO(Painel painel) {
        PainelDTO dto = new PainelDTO();
        dto.setId(painel.getId());
        dto.setNome(painel.getNome());
        dto.setLinkPowerBi(painel.getLinkPowerBi());
        dto.setImagemCapaUrl(painel.getImagemCapaUrl());
        dto.setStatusCaptura(painel.getStatusCaptura());
        return dto;
    }

    @Transactional
    public void deletarPainel(Long id) {
        if (!painelRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado.");
        }
        painelRepository.deleteById(id);
        logger.info("Painel ID: {} deletado.", id);
    }
}