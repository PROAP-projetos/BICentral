package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Optional;

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    private static final Logger logger = LoggerFactory.getLogger(AddPainelController.class);

    @Autowired
    private AddPainelRepository addPainelRepository;

    @Autowired
    private PowerBIScraperService scraperService;

    @PostMapping
    public ResponseEntity<?> criarPainel(@RequestBody AddPainel novoPainel) {
        Optional<AddPainel> painelExistente = addPainelRepository.findByLinkPowerBi(novoPainel.getLinkPowerBi());

        if (painelExistente.isPresent()) {
            return new ResponseEntity<>("Painel já cadastrado", HttpStatus.CONFLICT);
        }

        novoPainel.setStatusCaptura(AddPainel.StatusCaptura.PENDENTE);
        novoPainel.setImagemCapaUrl(null);

        AddPainel painelSalvo = addPainelRepository.save(novoPainel);

        try {
            scraperService.capturaCapaAsync(painelSalvo.getId());
        } catch (Exception e) {
            logger.error("Erro ao iniciar captura assíncrona para painel ID: {}", painelSalvo.getId(), e);
        }

        return new ResponseEntity<>(painelSalvo, HttpStatus.CREATED);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        return addPainelRepository.findAll();
    }
}
