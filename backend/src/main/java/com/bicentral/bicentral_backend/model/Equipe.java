package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private String descricao;

    // Relacionamento com Membros
    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY)
    @JsonIgnore // Importante: evita que ao pedir a Equipe, ele traga os membros, que trazem o usuario, que traz a equipe, e assim por diante (loop infinito)
    private Set<MembroEquipe> membros;
}