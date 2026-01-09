package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Painel;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PainelRepository extends JpaRepository<Painel, Long> {

    Optional<Painel> findByLinkPowerBi(String linkPowerBi);

    @Query("SELECT p FROM Painel p LEFT JOIN FETCH p.usuario")
    List<Painel> findAllWithUsuario();
}
