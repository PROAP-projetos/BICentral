package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    /**
     * Busca um usuário pelo nome de usuário.
     * @param username O nome de usuário a ser buscado.
     * @return Um Optional contendo o usuário encontrado ou vazio.
     */
    Optional<Usuario> findByNome(String username);

    /**
     * Busca um usuário pelo email.
     * @param email O email a ser buscado.
     * @return Um Optional contendo o usuário encontrado ou vazio.
     */
    Optional<Usuario> findByEmail(String email);

    Usuario findByVerificationToken(String token);

}