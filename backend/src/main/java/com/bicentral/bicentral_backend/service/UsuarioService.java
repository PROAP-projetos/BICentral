package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.exception.RecursoJaExistenteException;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService; // Injetando o serviço de Token
    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, 
                          EmailService emailService, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService; // Inicializando o JwtService
    }

    @Transactional
    public Usuario cadastrar(Usuario usuarioParaCadastrar, String siteURL) {
        if (usuarioRepository.findByNome(usuarioParaCadastrar.getNome()).isPresent()) {
            throw new RecursoJaExistenteException("O nome de usuário '" + usuarioParaCadastrar.getUsername() + "' já está em uso.");
        }
        if (usuarioRepository.findByEmail(usuarioParaCadastrar.getEmail()).isPresent()) {
            throw new RecursoJaExistenteException("O email '" + usuarioParaCadastrar.getEmail() + "' já está em uso.");
        }

        String senhaCodificada = passwordEncoder.encode(usuarioParaCadastrar.getPassword());
        usuarioParaCadastrar.setPassword(senhaCodificada);

        // UUID usado apenas para verificação de e-mail, NÃO para login
        String randomCode = UUID.randomUUID().toString();
        usuarioParaCadastrar.setVerificationToken(randomCode);
        usuarioParaCadastrar.setEnabled(false);

        Usuario savedUser = usuarioRepository.save(usuarioParaCadastrar);

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
            // Gera o Token real de 3 partes (ponto.ponto.ponto)
            return jwtService.generateToken(usuario); 
        } else {
            throw new AutenticacaoException("Email ou senha inválidos.");
        }
    }
}