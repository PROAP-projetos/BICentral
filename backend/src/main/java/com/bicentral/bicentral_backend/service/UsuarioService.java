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
public class UsuarioService{
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public Usuario cadastrar(Usuario usuarioParaCadastrar, String siteURL) {
        if (usuarioRepository.findByUsername(usuarioParaCadastrar.getUsername()).isPresent()) {
            throw new RecursoJaExistenteException("O nome de usuário '" + usuarioParaCadastrar.getUsername() + "' já está em uso.");
        }
        if (usuarioRepository.findByEmail(usuarioParaCadastrar.getEmail()).isPresent()) {
            throw new RecursoJaExistenteException("O email '" + usuarioParaCadastrar.getEmail() + "' já está em uso.");
        }

        String senhaCodificada = passwordEncoder.encode(usuarioParaCadastrar.getPassword());
        usuarioParaCadastrar.setPassword(senhaCodificada);

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

    /**
     * Autentica um usuário com base no email e senha.
     * A anotação @Transactional(readOnly = true) informa ao Spring e ao JPA
     * que esta é uma operação de apenas leitura, otimizando a performance.
     *
     * @param email O email do usuário.
     * @param senhaPlana A senha em texto plano fornecida pelo usuário.
     * @return O objeto Usuario se a autenticação for bem-sucedida.
     * @throws AutenticacaoException se o email não for encontrado ou a senha estiver incorreta.
     */
    @Transactional
    public Usuario login(String email, String senhaPlana) {
        // Busca o usuário pelo email ou lança uma exceção se não encontrar
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AutenticacaoException("Email ou senha inválidos."));

        // Verifica se a senha fornecida corresponde à senha armazenada (hash)
        if (passwordEncoder.matches(senhaPlana, usuario.getPassword())) {
            return usuario; // Login bem-sucedido
        } else {
            // A senha não corresponde
            throw new AutenticacaoException("Email ou senha inválidos.");
        }
    }
}