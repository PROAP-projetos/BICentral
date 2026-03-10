package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.EquipeRequestDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
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
    public MembroEquipe criarEquipe(Equipe newEquipe, Usuario criador){

        Equipe savedEquipe = equipeRepository.save(newEquipe);

        MembroEquipe novoMembro = new MembroEquipe();
        novoMembro.setUsuario(criador);
        novoMembro.setEquipe(savedEquipe);
        novoMembro.setRole(Role.ADMIN);

        return membroEquipeRepository.save(novoMembro);
    }

    @Transactional
    public void deletarEquipe(Long id, Usuario usuarioLogado){
        Equipe equipe = equipeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe membro = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Usuário não tem acesso a esta equipe"));

        if (membro.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas o administrador da equipe pode deletá-la");
        }

        equipeRepository.delete(equipe);
    }

    public List<MembroEquipe> listarMembros(Long equipeID){
        Equipe equipe = equipeRepository.findById(equipeID)
                .orElseThrow(() -> new RuntimeException("Equipe não encontada"));
        return membroEquipeRepository.findByEquipe(equipe);
    }

    public List<MembroEquipe> listarEquipesUsuario(Long userId){
        //primeiro valida o user
        Usuario user  = new Usuario();
        user.setId(userId);
        return membroEquipeRepository.findByUsuario(user);
    }

    @Transactional
    public MembroEquipe updateEquipe(Long id, EquipeRequestDTO dto, Usuario usuarioLogado){
        Equipe equipe = equipeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe membro = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Usuário não tem acesso a esta equipe"));

        if (membro.getRole() != Role.ADMIN && membro.getRole() != Role.EDITOR) {
            throw new AccessDeniedException("Permissão insuficiente para editar a equipe");
        }

        equipe.setNome(dto.nome());
        equipe.setDescricao(dto.descricao());
        equipeRepository.save(equipe);
        return membro;
    }
}
