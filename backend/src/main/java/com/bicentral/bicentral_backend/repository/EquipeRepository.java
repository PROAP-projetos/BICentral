package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    /**
     * Busca uma equipe pelo nome.
     * @param  equipe O nome da equipe a ser buscado.
     * @return Um Optional contendo a equipe encontrado ou vazio.
     */
    Optional<Equipe> findByNome(String equipe);

}
