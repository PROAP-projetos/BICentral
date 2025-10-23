// PainelDTO.java
package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.AddPainel;

public class PainelDTO {
    private String nome;
    private String linkPowerBi;
    private String imagemCapaBase64;
    private AddPainel.StatusCaptura statusCaptura;

    // Construtor
    public PainelDTO() {}

    // Construtor com todos os campos (opcional)
    public PainelDTO(String nome, String linkPowerBi, String imagemCapaBase64, AddPainel.StatusCaptura statusCaptura) {
        this.nome = nome;
        this.linkPowerBi = linkPowerBi;
        this.imagemCapaBase64 = imagemCapaBase64;
        this.statusCaptura = statusCaptura;
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

    public AddPainel.StatusCaptura getStatusCaptura() {
        return statusCaptura;
    }

    public void setStatusCaptura(AddPainel.StatusCaptura statusCaptura) {
        this.statusCaptura = statusCaptura;
    }

    public String getLinkPowerBi() {
        return linkPowerBi;
    }
}