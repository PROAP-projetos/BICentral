package com.bicentral.bicentral_backend.service.auth;

import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.exception.RecursoJaExistenteException;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService; // Injetando o serviço de Token
    private final TransactionTemplate transactionTemplate;
    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, 
                          EmailService emailService, JwtService jwtService, TransactionTemplate transactionTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService; // Inicializando o JwtService
        this.transactionTemplate = transactionTemplate;
    }

    public Usuario cadastrar(Usuario usuarioParaCadastrar, String siteURL) {
        Objects.requireNonNull(siteURL, "siteURL");

        String nomeNormalizado = usuarioParaCadastrar.getNome().trim();
        String emailNormalizado = usuarioParaCadastrar.getEmail().trim().toLowerCase();

        Usuario savedUser = transactionTemplate.execute(status -> {
            usuarioRepository.lockCadastroKey("cadastro:email:" + emailNormalizado);
            usuarioRepository.lockCadastroKey("cadastro:nome:" + nomeNormalizado.toLowerCase());

            if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
                throw new RecursoJaExistenteException("Este e-mail já está cadastrado. Faça login para continuar.");
            }
            if (usuarioRepository.existsByNomeIgnoreCase(nomeNormalizado)) {
                throw new RecursoJaExistenteException("Este nome de usuário já está em uso. Escolha outro para continuar.");
            }

            usuarioParaCadastrar.setNome(nomeNormalizado);
            usuarioParaCadastrar.setEmail(emailNormalizado);
            usuarioParaCadastrar.setPassword(passwordEncoder.encode(usuarioParaCadastrar.getPassword()));
            usuarioParaCadastrar.setVerificationToken(UUID.randomUUID().toString());
            usuarioParaCadastrar.setEnabled(false);

            try {
                return usuarioRepository.save(usuarioParaCadastrar);
            } catch (DataIntegrityViolationException ex) {
                String rootMessage = ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage();
                String normalizedMessage = rootMessage == null ? "" : rootMessage.toLowerCase();

                if (normalizedMessage.contains("email")) {
                    throw new RecursoJaExistenteException("Este e-mail já está cadastrado. Faça login para continuar.");
                }
                if (normalizedMessage.contains("username") || normalizedMessage.contains("nome")) {
                    throw new RecursoJaExistenteException("Este nome de usuário já está em uso. Escolha outro para continuar.");
                }
                throw ex;
            }
        });

        if (savedUser == null) {
            throw new RuntimeException("Erro ao processar cadastro.");
        }

        try {
            emailService.sendVerificationEmail(savedUser, siteURL);
        } catch (Exception e) {
            logger.error("Falha ao enviar e-mail de verificação", e);
            throw new RuntimeException("Erro ao enviar e-mail de verificação.");
        }

        return savedUser;
    }

    public boolean verify(String verificationCode) {
        Usuario user = usuarioRepository.findByVerificationToken(verificationCode);
        if (user == null || user.isEnabled()) {
            return false;
        } else {
            user.setVerificationToken(null);
            user.setEnabled(true);
            usuarioRepository.save(user);
            return true;
        }
    }

    @Transactional
    public String login(String email, String senhaPlana) { // Retorna String (o JWT)
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AutenticacaoException("Email ou senha inválidos."));

        if (passwordEncoder.matches(senhaPlana, usuario.getPassword())) {
            if (!usuario.isEnabled()) {
                throw new AutenticacaoException("Sua conta ainda não foi verificada. Verifique seu e-mail.");
            }
            // Gera o Token real de 3 partes (ponto.ponto.ponto)
            return jwtService.generateToken(usuario); 
        } else {
            throw new AutenticacaoException("Email ou senha inválidos.");
        }
    }

    public Usuario buscarPorEmail(String email){
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }
}
