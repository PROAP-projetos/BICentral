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
    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<MembroEquipe> membros;

    // Relacionamento com Paineis
    @OneToMany(mappedBy = "equipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Painel> paineis;
}