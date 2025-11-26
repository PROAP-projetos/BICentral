package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/paineis")
@CrossOrigin(origins = "http://localhost:4200")
public class PainelController {

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
            List<PainelDTO> resultado = new ArrayList<>();

            for (AddPainel painel : paineis) {
                PainelDTO dto = new PainelDTO();
                dto.setNome(painel.getNome());
                dto.setLinkPowerBi(painel.getLinkPowerBi());
                dto.setImagemCapaUrl(painel.getImagemCapaUrl()); // <-- atualizado
                dto.setStatusCaptura(painel.getStatusCaptura());

                resultado.add(dto);
            }

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
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
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao iniciar atualização de capa");
        }
    }
}
