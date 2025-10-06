package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {}


