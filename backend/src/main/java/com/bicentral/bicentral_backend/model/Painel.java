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

    // Mapeamento explícito para combinar com seu comando ALTER TABLE
    @Column(name = "link_power_bi", nullable = false, unique = true)
    private String linkPowerBi;

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