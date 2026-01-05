package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
public class Painel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String linkPowerBi;

    @Column(columnDefinition = "text")
    private String imagemCapaUrl;

    @Enumerated(EnumType.STRING)
    private StatusCaptura statusCaptura = StatusCaptura.PENDENTE;

    private LocalDateTime dataUltimaCaptura;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public enum StatusCaptura {
        PENDENTE,
        PROCESSANDO,
        CONCLUIDA,
        ERRO
    }
}
