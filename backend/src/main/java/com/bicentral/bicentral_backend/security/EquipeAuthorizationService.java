package com.bicentral.bicentral_backend.security;

import com.bicentral.bicentral_backend.model.MembroEquipe;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component("equipeSecurity") // O nome entre aspas permite usar no @PreAuthorize
public class EquipeAuthorizationService {

    @Autowired
    private MembroEquipeRepository membroEquipeRepository;

    /**
     * Verifica se o usuário logado tem um dos papéis necessários na equipe informada.
     */
    public boolean hasRole(Long equipeId, String... rolesPermitidos) {
        // 1. Pega o e-mail (username) do usuário autenticado no Spring Security
        String emailUsuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Busca se esse usuário é membro da equipe específica
        Optional<MembroEquipe> membro = membroEquipeRepository.findByUsuarioEmailAndEquipeId(emailUsuarioLogado, equipeId);

        // 3. Se não for membro, acesso negado (false)
        if (membro.isEmpty()) {
            return false;
        }

        // 4. Verifica se o papel dele (Role) está na lista de papéis que a rota exige
        String papelDoUsuario = membro.get().getRole().name(); // Pega o nome do Enum (ex: "ADMIN")

        return Arrays.asList(rolesPermitidos).contains(papelDoUsuario);
    }
}