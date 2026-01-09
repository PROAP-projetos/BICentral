package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.service.PainelService;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paineis")
@CrossOrigin(origins = "http://localhost:4200")
public class PainelController {

    private static final Logger logger = LoggerFactory.getLogger(PainelController.class);

    private final PainelRepository painelRepository;
    private final PainelService painelService;
    private final PowerBIScraperService scraperService;

    public PainelController(PainelRepository painelRepository,
                            PainelService painelService,
                            PowerBIScraperService scraperService) {
        this.painelRepository = painelRepository;
        this.painelService = painelService;
        this.scraperService = scraperService;
    }

    /**
     * POST /api/paineis - Cria um novo painel.
     * Retorna PainelDTO para garantir que o Front/Postman veja os dados preenchidos.
     */
    @PostMapping
    public ResponseEntity<PainelDTO> criarPainel(@Valid @RequestBody Painel novoPainel) {
        // O Service agora retorna o DTO já preenchido e sem valores nulos
        PainelDTO dtoSalvo = painelService.criarPainel(novoPainel);

        try {
            // Inicia a captura da capa usando o ID do DTO recém-criado
            scraperService.capturaCapaAsync(dtoSalvo.getId());
        } catch (Exception e) {
            logger.error("Erro ao iniciar captura assíncrona para ID: {}", dtoSalvo.getId(), e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(dtoSalvo);
    }

    /**
     * GET /api/paineis/com-capa - Lista todos os painéis.
     * Utiliza a lógica centralizada no Service para evitar repetição de código.
     */
    @GetMapping("/com-capa")
    public ResponseEntity<List<PainelDTO>> getAllPaineisComCapa() {
        try {
            // Chama o método listarTodos() que já faz o mapeamento correto para DTO
            List<PainelDTO> resultado = painelService.listarTodos();
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            logger.error("Erro ao listar painéis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/paineis/{id} - Remove um painel.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPainel(@PathVariable Long id) {
        try {
            painelService.deletarPainel(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Erro ao deletar painel ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/atualizar-capa/{id}")
    public ResponseEntity<String> atualizarCapa(@PathVariable Long id) {
        if (!painelRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        scraperService.capturaCapaAsync(id);
        return ResponseEntity.ok("Atualização de capa iniciada para ID: " + id);
    }
}