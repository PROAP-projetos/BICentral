package com.bicentral.bicentral_backend.controller.painel;

import com.bicentral.bicentral_backend.dto.painel.PainelDTO;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.service.equipe.PainelService;
import com.bicentral.bicentral_backend.service.equipe.PowerBIScraperService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/equipes/{equipeId}/paineis") //nova rota

public class PainelController {

    private static final Logger logger = LoggerFactory.getLogger(PainelController.class);

    private final PainelService painelService;
    private final PowerBIScraperService scraperService;

    public PainelController(PainelService painelService,
                            PowerBIScraperService scraperService) {
        this.painelService = painelService;
        this.scraperService = scraperService;
    }

    @PostMapping
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'EDITOR', 'ADMIN')")
    public ResponseEntity<PainelDTO> criarPainel(@PathVariable Long equipeId, @Valid @RequestBody Painel novoPainel) {
        PainelDTO dtoSalvo = painelService.criarPainel(equipeId, novoPainel);

        // dispara scraping async (não derruba criação se falhar)
        try {
            scraperService.capturaCapaAsync(dtoSalvo.getId());
        } catch (Exception e) {
            logger.error("Erro ao iniciar captura assíncrona para ID: {}", dtoSalvo.getId(), e);
        }

        URI location = URI.create("/api/equipes/" + equipeId + "/paineis/" + dtoSalvo.getId());
        return ResponseEntity.created(location).body(dtoSalvo);
    }

    @GetMapping  //("/com-capa")
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<List<PainelDTO>> listar(@PathVariable Long equipeId) {
        return ResponseEntity.ok(painelService.listarPorEquipe(equipeId));
    }

   /* @GetMapping
    public ResponseEntity<List<PainelDTO>> listar() {
        return ResponseEntity.ok(painelService.listarMeusPaineis());
    } */

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<PainelDTO> buscarPorId(@PathVariable Long equipeId, @PathVariable Long id) {
        return ResponseEntity.ok(painelService.buscarPorIdAndEquipe(id, equipeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'EDITOR', 'ADMIN')")
    public ResponseEntity<PainelDTO> atualizar(@PathVariable Long equipeId, @PathVariable Long id, @Valid @RequestBody PainelDTO dto) {
        PainelDTO atualizado = painelService.atualizarPainel(id, equipeId, dto);
        return ResponseEntity.ok(atualizado);
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'EDITOR', 'ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable Long equipeId, @PathVariable Long id) {
        painelService.deletarPainel(id, equipeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/atualizar-capa")
    @PreAuthorize("@equipeSecurity.hasRole(#equipeId, 'EDITOR', 'ADMIN')")
    public ResponseEntity<String> atualizarCapa(@PathVariable Long equipeId, @PathVariable Long id) {
        // valida se existe e se é do usuário
        painelService.buscarPorIdAndEquipe(id, equipeId);

        scraperService.capturaCapaAsync(id);
        return ResponseEntity.ok("Atualização de capa iniciada para ID: " + id);
    }
}
