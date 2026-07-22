package com.bicentral.bicentral_backend.dto.admin;

    public record GerenteDepartamentoRequestDTO(
        Long usuarioId, 
        String departamento, 
        String tipoUnidade
) {}