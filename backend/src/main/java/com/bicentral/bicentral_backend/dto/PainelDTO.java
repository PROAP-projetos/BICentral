// PainelDTO.java
package com.bicentral.bicentral_backend.dto; // Remova o 'public' e a linha 'package dto;'

public class PainelDTO {
    private String nome;
    private String linkPowerBi;
    private String imagemCapaBase64; 

    // Construtor
    public PainelDTO() {}

    // Construtor com todos os campos (opcional)
    public PainelDTO(String nome, String linkPowerBi, String imagemCapaBase64) {
        this.nome = nome;
        this.linkPowerBi = linkPowerBi;
        this.imagemCapaBase64 = imagemCapaBase64;
    }

    // GETTERS e SETTERS 
    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
    public void setLinkPowerBi(String linkPowerBi) {
        this.linkPowerBi = linkPowerBi;
    }
    public String getImagemCapaBase64() {
        return imagemCapaBase64;
    }
    public void setImagemCapaBase64(String imagemCapaBase64) {
        this.imagemCapaBase64 = imagemCapaBase64;
    }
}