package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.service.PainelService;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/paineis")
@CrossOrigin(origins = "http://localhost:4200")
public class PainelController {

    private static final Logger logger = LoggerFactory.getLogger(PainelController.class);

    private final PainelService painelService;
    private final PowerBIScraperService scraperService;

    public PainelController(PainelService painelService,
                            PowerBIScraperService scraperService) {
        this.painelService = painelService;
        this.scraperService = scraperService;
    }

    // ✅ CREATE
    @PostMapping
    public ResponseEntity<PainelDTO> criarPainel(@Valid @RequestBody Painel novoPainel) {
        PainelDTO dtoSalvo = painelService.criarPainel(novoPainel);

        // dispara scraping async (não derruba criação se falhar)
        try {
            scraperService.capturaCapaAsync(dtoSalvo.getId());
        } catch (Exception e) {
            logger.error("Erro ao iniciar captura assíncrona para ID: {}", dtoSalvo.getId(), e);
        }

        return ResponseEntity
                .created(URI.create("/api/paineis/" + dtoSalvo.getId()))
                .body(dtoSalvo);
    }

    /**
     * ✅ READ - listagem "home"
     * Front está chamando: GET /api/paineis/com-capa
     */
    @GetMapping("/com-capa")
    public ResponseEntity<List<PainelDTO>> listarComCapa() {
        return ResponseEntity.ok(painelService.listarMeusPaineis());
    }

    /**
     * ✅ READ - listagem padrão
     * Melhor: manter também como "meus painéis" (pra não vazar dados).
     * Se quiser manter o /com-capa no front, ok. Mas este endpoint é útil também.
     */
    @GetMapping
    public ResponseEntity<List<PainelDTO>> listar() {
        return ResponseEntity.ok(painelService.listarMeusPaineis());
    }

    /**
     * ✅ READ - by id
     * Importante: restringe a números pra nunca conflitar com /com-capa.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<PainelDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(painelService.buscarPorId(id));
    }

    // ✅ UPDATE (PUT)
    @PutMapping("/{id}")
    public ResponseEntity<PainelDTO> atualizar(@PathVariable Long id, @Valid @RequestBody PainelDTO dto) {
        PainelDTO atualizado = painelService.atualizarPainel(id, dto);
        return ResponseEntity.ok(atualizado);
    }

    // ✅ DELETE
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        painelService.deletarPainel(id);
        return ResponseEntity.noContent().build();
    }

    // ✅ Ação manual (não é CRUD)
    @PostMapping("/{id:\\d+}/atualizar-capa")
    public ResponseEntity<String> atualizarCapa(@PathVariable Long id) {
        // valida se existe e se é do usuário
        painelService.buscarPorId(id);

        scraperService.capturaCapaAsync(id);
        return ResponseEntity.ok("Atualização de capa iniciada para ID: " + id);
    }
}
