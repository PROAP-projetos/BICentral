package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.dto.EquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.MembroEquipeRequestDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EquipeService {
    private final EquipeRepository equipeRepository;
    private final MembroEquipeRepository membroEquipeRepository;
    private final UsuarioRepository usuarioRepository;

    public EquipeService(EquipeRepository equipeRepository, MembroEquipeRepository membroEquipeRepository, UsuarioRepository usuarioRepository) {
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
        this.usuarioRepository = usuarioRepository;
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

    @Transactional
    public MembroEquipe adicionarMembro(Long equipeId, MembroEquipeRequestDTO dto, Usuario usuarioLogado) {
        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe quemAdiciona = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta equipe"));

        if (quemAdiciona.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem adicionar membros");
        }

        Usuario novoUsuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        if (membroEquipeRepository.findByUsuarioAndEquipe(novoUsuario, equipe).isPresent()) {
            throw new RuntimeException("Usuário já é membro desta equipe");
        }

        MembroEquipe novoMembro = new MembroEquipe();
        novoMembro.setUsuario(novoUsuario);
        novoMembro.setEquipe(equipe);
        novoMembro.setRole(dto.role());

        return membroEquipeRepository.save(novoMembro);
    }

    @Transactional
    public void removerMembro(Long equipeId, Long usuarioId, Usuario usuarioLogado) {
        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe quemRemove = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta equipe"));

        if (quemRemove.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem remover membros");
        }

        Usuario usuarioParaRemover = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        MembroEquipe membroParaRemover = membroEquipeRepository.findByUsuarioAndEquipe(usuarioParaRemover, equipe)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não é membro desta equipe"));

        // Não permitir remover a si mesmo se for o único admin (opcional, mas recomendado)
        if (usuarioParaRemover.getId().equals(usuarioLogado.getId())) {
             long adminsCount = membroEquipeRepository.findByEquipe(equipe).stream()
                     .filter(m -> m.getRole() == Role.ADMIN).count();
             if (adminsCount <= 1) {
                 throw new RuntimeException("Não é possível remover o único administrador da equipe");
             }
        }

        membroEquipeRepository.delete(membroParaRemover);
    }

    @Transactional
    public MembroEquipe alterarPapelMembro(Long equipeId, Long usuarioId, Role novoRole, Usuario usuarioLogado) {
        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe quemAltera = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta equipe"));

        if (quemAltera.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem alterar papéis");
        }

        Usuario usuarioMembro = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        MembroEquipe membro = membroEquipeRepository.findByUsuarioAndEquipe(usuarioMembro, equipe)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não é membro desta equipe"));

        membro.setRole(novoRole);
        return membroEquipeRepository.save(membro);
    }
}
