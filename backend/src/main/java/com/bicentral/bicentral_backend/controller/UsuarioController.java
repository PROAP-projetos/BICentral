package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")

public class UsuarioController {
    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/cadastro")
    public Usuario cadastrarUsuario(@RequestBody Usuario usuario) {
        return usuarioRepository.save(usuario);
    }
}



