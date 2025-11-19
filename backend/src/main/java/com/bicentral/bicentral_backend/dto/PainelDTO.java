// PainelDTO.java
package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.AddPainel;

public class PainelDTO {
    private String nome;
    private String linkPowerBi;
    private String imagemCapaUrl; // <-- renomeado
    private AddPainel.StatusCaptura statusCaptura;

    // Construtor vazio
    public PainelDTO() {}

    // Construtor completo
    public PainelDTO(String nome, String linkPowerBi, String imagemCapaUrl, AddPainel.StatusCaptura statusCaptura) {
        this.nome = nome;
        this.linkPowerBi = linkPowerBi;
        this.imagemCapaUrl = imagemCapaUrl; // <-- renomeado
        this.statusCaptura = statusCaptura;
    }

    // GETTERS e SETTERS
    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getLinkPowerBi() {
        return linkPowerBi;
    }

    public void setLinkPowerBi(String linkPowerBi) {
        this.linkPowerBi = linkPowerBi;
    }

    public String getImagemCapaUrl() { // <-- renomeado
        return imagemCapaUrl;
    }

    public void setImagemCapaUrl(String imagemCapaUrl) { // <-- renomeado
        this.imagemCapaUrl = imagemCapaUrl;
    }

    public AddPainel.StatusCaptura getStatusCaptura() {
        return statusCaptura;
    }

    public void setStatusCaptura(AddPainel.StatusCaptura statusCaptura) {
        this.statusCaptura = statusCaptura;
    }
}
