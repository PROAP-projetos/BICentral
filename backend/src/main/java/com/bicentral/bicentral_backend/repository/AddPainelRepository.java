package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.AddPainel; // Importação atualizada
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; // Importar Optional

public interface AddPainelRepository extends JpaRepository<AddPainel, Long> { // Renomeado

    Optional<AddPainel> findByLinkPowerBi(String linkPowerBi);
}