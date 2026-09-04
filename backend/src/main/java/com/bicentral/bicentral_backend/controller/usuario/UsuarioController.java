package com.bicentral.bicentral_backend.controller.usuario;

import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.exception.RecursoJaExistenteException;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.UsoIaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final UsoIaService usoIaService;

    public UsuarioController(UsuarioService usuarioService, UsuarioRepository usuarioRepository, UsoIaService usoIaService) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.usoIaService = usoIaService;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrarUsuario(@Valid @RequestBody Usuario usuario, HttpServletRequest request) {
        try {
            // Quem já foi convidado como tester do proIAp por e-mail pula a verificação de
            // e-mail no cadastro (ver UsoIaService.emailTesterPendente).
            boolean pularVerificacao = usoIaService.emailTesterPendente(usuario.getEmail());
            Usuario cadastrado = usuarioService.cadastrar(usuario, getSiteURL(request), pularVerificacao);
            // Se esse e-mail já tinha sido adicionado como tester do proIAp antes de existir
            // conta, vira tester de verdade agora (ver UsoIaService.promoverPendentesParaTester).
            usoIaService.promoverPendentesParaTester(cadastrado.getId(), cadastrado.getEmail());
            Map<String, String> response = new HashMap<>();
            response.put("mensagem", pularVerificacao
                    ? "Cadastro realizado! Você já pode entrar — como tester do proIAp, não precisa verificar o e-mail."
                    : "Cadastro realizado com sucesso! Verifique seu e-mail para ativar sua conta.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RecursoJaExistenteException e) {
            Map<String, String> response = new HashMap<>();
            response.put("mensagem", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("mensagem", "Erro ao processar cadastro. Tente novamente.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestParam("code") String code) {
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
        try {
            String token = usuarioService.login(loginRequest.getEmail(), loginRequest.getPassword());

            Usuario usuario = usuarioRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("Erro ao recuperar dados do usuário."));

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", usuario.getNomeExibicao());
            response.put("id", usuario.getId().toString());
            // Tester do proIAp cai direto no agente em vez da Home após o login (ver LoginComponent).
            response.put("tester", usoIaService.ehTester(usuario.getId()));
            return ResponseEntity.ok(response);

        } catch (AutenticacaoException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno no servidor: " + e.getMessage());
        }
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
