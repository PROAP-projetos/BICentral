package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembroEquipeRepository extends JpaRepository<MembroEquipe, Long> {

    Optional<MembroEquipe> findByUsuarioAndEquipe(Usuario user, Equipe equipe);

    Optional<MembroEquipe> findByEquipeIdAndUsuarioId(Long equipeId, Long usuarioId);
    boolean existsByEquipeIdAndUsuarioIdAndRole(Long equipeId, Long usuarioId, Role role);
    
    List<MembroEquipe> findByUsuario(Usuario user);

    List<MembroEquipe> findByEquipe(Equipe equipe);

    Optional<MembroEquipe> findByUsuarioEmailAndEquipeId(String email, Long equipeId);

}
