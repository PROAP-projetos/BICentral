package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Painel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PainelRepository extends JpaRepository<Painel, Long> {
    boolean existsByLinkPowerBiAndEquipeId(String linkPowerBi, Long equipeId);

    boolean existsByLinkPowerBiAndEquipeIdAndIdNot(String linkPowerBi, Long equipeId, Long id);

    @Query("""
            select p
            from Painel p
            where p.equipe.id = :equipeId
                and exists (
                        select 1
                        from MembroEquipe m
                        where m.equipe.id = :equipeId
                            and m.usuario.email = :email
                )
            """)
    List<Painel> findByEquipeIdForMember(@Param("equipeId") Long equipeId, @Param("email") String email);

    @Query("""
            select p
            from Painel p
            where p.id = :id
                and p.equipe.id = :equipeId
                and exists (
                        select 1
                        from MembroEquipe m
                        where m.equipe.id = :equipeId
                            and m.usuario.email = :email
                )
            """)
    Optional<Painel> findByIdAndEquipeIdForMember(
            @Param("id") Long id,
            @Param("equipeId") Long equipeId,
            @Param("email") String email
    );
}
