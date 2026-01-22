package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EquipeService {
    private final EquipeRepository equipeRepository;
    private final MembroEquipeRepository membroEquipeRepository;

    public EquipeService(EquipeRepository equipeRepository, MembroEquipeRepository membroEquipeRepository) {
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
    }

    @Transactional
    public Equipe criarEquipe(Equipe newEquipe, Usuario criador){

        Equipe savedEquipe = equipeRepository.save(newEquipe);

        MembroEquipe novoMembro = new MembroEquipe();
        novoMembro.setUsuario(criador);
        novoMembro.setEquipe(savedEquipe);
        novoMembro.setRole(Role.ADMIN);

        membroEquipeRepository.save(novoMembro);
        return savedEquipe;
    }

    public List<MembroEquipe> listarMembros(Long equipeID){
        Equipe equipe = equipeRepository.findById(equipeID)
                .orElseThrow(() -> new RuntimeException("Equipe não encontada"));
        return membroEquipeRepository.findByEquipe(equipe);
    }

}
