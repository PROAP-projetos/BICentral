package com.bicentral.bicentral_backend.dto.equipe;

import com.bicentral.bicentral_backend.model.Role;

public record MembroEquipeRequestDTO(String email, Role role) {
}
