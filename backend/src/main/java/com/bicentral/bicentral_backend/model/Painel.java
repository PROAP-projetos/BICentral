package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "add_painel")
@NoArgsConstructor
public class Painel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    public LocalDateTime getDataUltimaCaptura() {
        return dataUltimaCaptura;
    }

    public void setDataUltimaCaptura(LocalDateTime dataUltimaCaptura) {
        this.dataUltimaCaptura = dataUltimaCaptura;
    }

    public StatusCaptura getStatusCaptura() {
        return statusCaptura;
    }

    public void setStatusCaptura(StatusCaptura statusCaptura) {
        this.statusCaptura = statusCaptura;
    }

    public String getImagemCapaUrl() {
        return imagemCapaUrl;
    }

    public void setImagemCapaUrl(String imagemCapaUrl) {
        this.imagemCapaUrl = imagemCapaUrl;
    }

    public String getLinkPowerBi() {
        return linkPowerBi;
    }

    public void setLinkPowerBi(String linkPowerBi) {
        this.linkPowerBi = linkPowerBi;
    }

    // Mapeamento explícito para combinar com seu comando ALTER TABLE
    @Column(name = "link_power_bi", nullable = false, unique = true)
    private String linkPowerBi;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    @Column(name = "imagem_capa_url", columnDefinition = "text")
    private String imagemCapaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_captura")
    private StatusCaptura statusCaptura = StatusCaptura.PENDENTE;

    @Column(name = "data_ultima_captura")
    private LocalDateTime dataUltimaCaptura;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    //relacionando com equipe
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id")
    private Equipe equipe;

    public enum StatusCaptura {
        PENDENTE, PROCESSANDO, CONCLUIDA, ERRO
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (this.statusCaptura == StatusCaptura.CONCLUIDA) {
            this.dataUltimaCaptura = LocalDateTime.now();
        }
    }
}