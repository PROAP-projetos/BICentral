package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.ConviteEquipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConviteEquipeRepository extends JpaRepository<ConviteEquipe, Long> {

    Optional<ConviteEquipe> findByToken(String token);

    Optional<ConviteEquipe> findByEquipeIdAndEmailAndStatus(Long equipeId, String email, ConviteEquipe.Status status);
}
