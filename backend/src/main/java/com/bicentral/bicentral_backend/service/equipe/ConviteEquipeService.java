package com.bicentral.bicentral_backend.service.equipe;

import com.bicentral.bicentral_backend.dto.equipe.AceiteConviteResponseDTO;
import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeRequestDTO;
import com.bicentral.bicentral_backend.dto.equipe.ConviteEquipeResponseDTO;
import com.bicentral.bicentral_backend.model.ConviteEquipe;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.model.Role;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.ConviteEquipeRepository;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import com.bicentral.bicentral_backend.service.auth.EmailService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class ConviteEquipeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ConviteEquipeRepository conviteEquipeRepository;
    private final EquipeRepository equipeRepository;
    private final MembroEquipeRepository membroEquipeRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    @Value("${convite.equipe.expiracao-horas:72}")
    private long expiracaoHoras;

    public ConviteEquipeService(
            ConviteEquipeRepository conviteEquipeRepository,
            EquipeRepository equipeRepository,
            MembroEquipeRepository membroEquipeRepository,
            UsuarioRepository usuarioRepository,
            EmailService emailService
    ) {
        this.conviteEquipeRepository = conviteEquipeRepository;
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
    }

    @Transactional
    public ConviteEquipeResponseDTO enviarConvite(
            Long equipeId,
            ConviteEquipeRequestDTO dto,
            Usuario usuarioLogado,
            String siteUrl
    ) {
        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe não encontrada"));

        MembroEquipe membroLogado = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta equipe"));

        if (membroLogado.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem enviar convites");
        }

        String emailNormalizado = normalizarEmail(dto.email());

        usuarioRepository.findByEmail(emailNormalizado)
                .flatMap(usuario -> membroEquipeRepository.findByUsuarioAndEquipe(usuario, equipe))
                .ifPresent(membro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já é membro desta equipe.");
                });

        ConviteEquipe convite = conviteEquipeRepository
                .findByEquipeIdAndEmailAndStatus(equipeId, emailNormalizado, ConviteEquipe.Status.PENDENTE)
                .orElseGet(ConviteEquipe::new);

        convite.setEquipe(equipe);
        convite.setCriadoPor(usuarioLogado);
        convite.setEmail(emailNormalizado);
        convite.setRole(dto.role());
        convite.setToken(gerarTokenSeguro());
        convite.setStatus(ConviteEquipe.Status.PENDENTE);
        convite.setAceitoEm(null);
        convite.setExpiraEm(LocalDateTime.now().plusHours(expiracaoHoras));

        ConviteEquipe salvo = conviteEquipeRepository.save(convite);

        try {
            emailService.sendTeamInviteEmail(
                    equipe,
                    emailNormalizado,
                    dto.role(),
                    buildInviteUrl(siteUrl, salvo.getToken()),
                    salvo.getExpiraEm()
            );
        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar e-mail de convite.", e);
        }

        return new ConviteEquipeResponseDTO(salvo);
    }

    @Transactional
    public AceiteConviteResponseDTO aceitarConvite(String token, Usuario usuarioLogado) {
        ConviteEquipe convite = conviteEquipeRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido."));

        validarConviteParaAceite(convite);

        if (!normalizarEmail(convite.getEmail()).equals(normalizarEmail(usuarioLogado.getEmail()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Este convite foi enviado para outro e-mail (" + convite.getEmail() + "). Faça login com essa conta para aceitá-lo."
            );
        }

        MembroEquipe membro = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, convite.getEquipe())
                .orElseGet(() -> {
                    MembroEquipe novoMembro = new MembroEquipe();
                    novoMembro.setUsuario(usuarioLogado);
                    novoMembro.setEquipe(convite.getEquipe());
                    return novoMembro;
                });

        membro.setRole(convite.getRole());
        membroEquipeRepository.save(membro);

        convite.setStatus(ConviteEquipe.Status.ACEITO);
        convite.setAceitoEm(LocalDateTime.now());
        conviteEquipeRepository.save(convite);

        return new AceiteConviteResponseDTO(
                "Convite aceito com sucesso.",
                convite.getEquipe().getId(),
                convite.getEquipe().getNome(),
                convite.getEmail(),
                convite.getRole()
        );
    }

    private void validarConviteParaAceite(ConviteEquipe convite) {
        if (convite.getStatus() == ConviteEquipe.Status.ACEITO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este convite já foi utilizado.");
        }

        if (convite.getStatus() == ConviteEquipe.Status.EXPIRADO) {
            throw new ResponseStatusException(HttpStatus.GONE, "Este convite expirou.");
        }

        if (convite.getExpiraEm().isBefore(LocalDateTime.now())) {
            convite.setStatus(ConviteEquipe.Status.EXPIRADO);
            conviteEquipeRepository.save(convite);
            throw new ResponseStatusException(HttpStatus.GONE, "Este convite expirou.");
        }

        if (convite.getStatus() != ConviteEquipe.Status.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este convite não está mais disponível.");
        }
    }

    private String buildInviteUrl(String siteUrl, String token) {
        String baseUrl = frontendBaseUrl != null && !frontendBaseUrl.isBlank()
                ? frontendBaseUrl.trim()
                : siteUrl;
        return baseUrl.replaceAll("/$", "") + "/aceitar-convite?token=" + token;
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String gerarTokenSeguro() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
