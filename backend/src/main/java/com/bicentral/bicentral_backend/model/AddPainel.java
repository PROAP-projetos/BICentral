package com.bicentral.bicentral_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor; // Adicionado para boas práticas

@Data
@Entity
@NoArgsConstructor // Adicionado: Construtor vazio é necessário pelo JPA
public class AddPainel { // Renomeado para começar com letra maiúscula

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String linkPowerBi;
}