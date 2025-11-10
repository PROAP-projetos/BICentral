package com.bicentral.bicentral_backend.controller;

// Importações necessárias
import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Usuario;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Importado para usar o HttpStatus
import org.springframework.http.ResponseEntity;
import com.bicentral.bicentral_backend.service.UsuarioService;


@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "http://localhost:4200")
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

    /* * ================================================================
     * MÉTODO DE LOGIN CORRIGIDO
     * Adicionado um catch genérico (Exception e) para capturar
     * erros de banco de dados (como o de rollback) e evitar o 403.
     * ================================================================
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            // 1. Tenta fazer o login
            Usuario usuario = usuarioService.login(loginRequest.getEmail(), loginRequest.getPassword());

            // 2. Se der certo, retorna 200 OK
            return ResponseEntity.ok(usuario);

        } catch (AutenticacaoException e) {
            // 3. Captura o erro de "senha inválida"
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());

        } catch (Exception e) {
            // 4. Captura QUALQUER OUTRO erro (como o do banco de dados)
            // e retorna um Erro Interno do Servidor (500)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno no servidor: " + e.getMessage());
        }
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