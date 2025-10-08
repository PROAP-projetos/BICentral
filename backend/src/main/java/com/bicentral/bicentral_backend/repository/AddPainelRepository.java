package com.bicentral.bicentral_backend.repository;

import com.bicentral.bicentral_backend.model.AddPainel; // Importação atualizada
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddPainelRepository extends JpaRepository<AddPainel, Long> { // Renomeado
}