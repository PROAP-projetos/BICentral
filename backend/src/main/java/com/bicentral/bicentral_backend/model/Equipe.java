package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Nome não pode estar em branco")
    @Size(min = 3, max = 20, message = "Nome deve ter entre 3 e 20 caracteres")
    @Column(name = "nome", unique = true)
    private String nome;

    @Size (min = 3, max = 500, message = "Descrição deve ter entre 3 e 500 caracteres")
    @Column(name = "descricao", unique = false)
    private String descricao;

    // Relacionamento com Membros
    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY)
    @JsonIgnore // Importante: evita que ao pedir a Equipe, ele traga os membros, que trazem o usuario, que traz a equipe, e assim por diante (loop infinito)
    private Set<MembroEquipe> membros;
}