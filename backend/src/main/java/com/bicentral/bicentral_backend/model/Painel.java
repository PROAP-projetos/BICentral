package com.bicentral.bicentral_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Painel {
    
    @Id
    private Long id;
    
    private String nome;
    private String linkPowerBi;
    
    // Construtor 
    public Painel() {}
    
    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getLinkPowerBi() { 
        return linkPowerBi;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setLinkPowerBi(String linkPowerBi) {
        this.linkPowerBi = linkPowerBi;
    }
}