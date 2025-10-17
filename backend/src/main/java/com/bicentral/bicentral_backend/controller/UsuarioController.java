package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.bicentral.bicentral_backend.service.UsuarioService;
import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/auth")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrarUsuario(@Valid @RequestBody Usuario usuario, HttpServletRequest request) {
        try {
            Usuario novoUsuario = usuarioService.cadastrar(usuario, getSiteURL(request));
            return ResponseEntity.ok(novoUsuario);
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(@Param("code") String code) {
        if (usuarioService.verify(code)) {
            return ResponseEntity.ok("verify_success");
        } else {
            return ResponseEntity.badRequest().body("verify_fail");
        }
    }

    private String getSiteURL(HttpServletRequest request) {
        String siteURL = request.getRequestURL().toString();
        return siteURL.replace(request.getServletPath(), "");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        Usuario usuario = usuarioService.login(loginRequest.getEmail(), loginRequest.getPassword());
        if (usuario != null) {
            return ResponseEntity.ok(usuario);
        }
        return ResponseEntity.status(401).body("Credenciais inválidas");
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}




