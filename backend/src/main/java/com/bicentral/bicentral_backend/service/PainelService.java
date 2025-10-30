package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PainelService {
    
    private final PainelRepository painelRepository;

    // Injeção de dependência do repositório via construtor
    public PainelService(PainelRepository painelRepository) {
        this.painelRepository = painelRepository;
    }

    /**
     * Busca todos os painéis salvos no banco de dados.
     * Este é o método que o PainelController chama.
     * @return Lista de entidades Painel.
     */
    public List<Painel> findAll() {
        return painelRepository.findAll();
    }
}