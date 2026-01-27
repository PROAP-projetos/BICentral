package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
public class MembroEquipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id") // Cria a coluna usuario_id no banco
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "equipe_id") // Cria a coluna equipe_id no banco
    private Equipe equipe;

    @Enumerated(EnumType.STRING)
    private Role role;
}