// PainelRepository.java (no pacote repository)
package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Painel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PainelRepository extends JpaRepository<Painel, Long> {
    // Não precisa de métodos aqui, JpaRepository já fornece o findAll()
}