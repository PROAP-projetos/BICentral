package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/paineis")
@CrossOrigin(origins = "http://localhost:4200")
public class PainelController {

    private static final Logger logger = LoggerFactory.getLogger(PainelController.class);

    private final AddPainelRepository addPainelRepository;
    private final PowerBIScraperService scraperService;

    public PainelController(AddPainelRepository addPainelRepository, PowerBIScraperService scraperService) {
        this.addPainelRepository = addPainelRepository;
        this.scraperService = scraperService;
    }

    @GetMapping("/teste")
    public ResponseEntity<String> teste() {
        return ResponseEntity.ok("API funcionando!");
    }

    /**
     * Endpoint principal. Busca a lista de painéis no banco e retorna as capas (URLs) já armazenadas.
     */
    @GetMapping("/com-capa")
    public ResponseEntity<List<PainelDTO>> getAllPaineisComCapa() {
        try {
            List<AddPainel> paineis = addPainelRepository.findAll();
            List<PainelDTO> resultado = paineis.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            logger.error("Erro ao buscar painéis com capa", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    @PostMapping("/atualizar-capa/{id}")
    public ResponseEntity<String> atualizarCapa(@PathVariable Long id) {
        try {
            if (!addPainelRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            scraperService.capturaCapaAsync(id);
            return ResponseEntity.ok("Atualização de capa iniciada para painel ID: " + id);

        } catch (Exception e) {
            logger.error("Erro ao iniciar atualização de capa para painel ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao iniciar atualização de capa");
        }
    }

    private PainelDTO toDTO(AddPainel painel) {
        return new PainelDTO(
                painel.getNome(),
                painel.getLinkPowerBi(),
                painel.getImagemCapaUrl(),
                painel.getStatusCaptura()
        );
    }
}
