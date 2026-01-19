package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.Painel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PainelRepository extends JpaRepository<Painel, Long> {

    // ✅ duplicidade na vitrine do usuário (não global)
    boolean existsByLinkPowerBiAndUsuario_Id(String linkPowerBi, Long usuarioId);

    // ✅ útil se você quiser buscar o painel pelo link dentro do dono
    Optional<Painel> findByLinkPowerBiAndUsuario_Id(String linkPowerBi, Long usuarioId);

    // ✅ segurança: só mexe no que é dele
    Optional<Painel> findByIdAndUsuario_Id(Long id, Long usuarioId);

    // ✅ update: checa duplicata ignorando o próprio painel
    boolean existsByLinkPowerBiAndUsuario_IdAndIdNot(String linkPowerBi, Long usuarioId, Long id);

    // ✅ listagem do dono
    List<Painel> findAllByUsuario_Id(Long usuarioId);
}
