package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PainelService {

    private static final Logger logger = LoggerFactory.getLogger(PainelService.class);

    @Autowired
    private PainelRepository painelRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public Painel criarPainel(Painel painel) {
        // Obtém o email do usuário autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || authentication.getName() == null || authentication.getName().equals("anonymousUser")) {
            throw new AutenticacaoException("Usuário não autenticado");
        }

        String email = authentication.getName();
        logger.debug("Buscando usuário com email: {}", email);

        // Busca o usuário no banco de dados
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        
        if (usuarioOpt.isEmpty()) {
            logger.error("Usuário não encontrado com email: {}", email);
            throw new AutenticacaoException("Usuário não encontrado");
        }

        Usuario usuario = usuarioOpt.get();

        // Verifica se já existe painel com o mesmo link
        Optional<Painel> painelExistente = painelRepository.findByLinkPowerBi(painel.getLinkPowerBi());
        if (painelExistente.isPresent()) {
            throw new RuntimeException("Painel já cadastrado");
        }

        // Associa o usuário ao painel
        painel.setUsuario(usuario);
        painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
        painel.setImagemCapaUrl(null);

        // Salva o painel
        Painel painelSalvo = painelRepository.save(painel);
        logger.info("Painel criado com sucesso. ID: {}, Usuário: {}", painelSalvo.getId(), email);

        return painelSalvo;
    }
}

