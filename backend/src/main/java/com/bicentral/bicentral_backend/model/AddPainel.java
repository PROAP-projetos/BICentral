package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
public class AddPainel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String linkPowerBi;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String imagemCapaUrl;

    @Enumerated(EnumType.STRING)
    private StatusCaptura statusCaptura = StatusCaptura.PENDENTE;

    private LocalDateTime dataUltimaCaptura;

    public enum StatusCaptura {
        PENDENTE,
        PROCESSANDO,
        CONCLUIDA,
        ERRO
    }
}