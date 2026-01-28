package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "membros_equipe",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipe_id", "usuario_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembroEquipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id", nullable = false)
    private Equipe equipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PapelMembro papel = PapelMembro.MEMBRO;

    @Column(name = "adicionado_em")
    private LocalDateTime adicionadoEm;

    @Column(name = "convite_aceito")
    @Builder.Default
    private Boolean conviteAceito = true; // Para sistemas com convites

    @PrePersist
    protected void onCreate() {
        if (adicionadoEm == null) {
            adicionadoEm = LocalDateTime.now();
        }
    }
}