package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.service.PainelService;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    private static final Logger logger = LoggerFactory.getLogger(AddPainelController.class);

    @Autowired
    private PainelService painelService;

    @Autowired
    private PainelRepository painelRepository;

    @Autowired
    private PowerBIScraperService scraperService;

    @PostMapping
    public ResponseEntity<?> criarPainel(@RequestBody Painel novoPainel) {
        try {
            Painel painelSalvo = painelService.criarPainel(novoPainel);

            try {
                scraperService.capturaCapaAsync(painelSalvo.getId());
            } catch (Exception e) {
                logger.error("Erro ao iniciar captura assíncrona para painel ID: {}", painelSalvo.getId(), e);
            }

            return new ResponseEntity<>(painelSalvo, HttpStatus.CREATED);
        } catch (AutenticacaoException e) {
            logger.error("Erro de autenticação ao criar painel: {}", e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().equals("Painel já cadastrado")) {
                return new ResponseEntity<>("Painel já cadastrado", HttpStatus.CONFLICT);
            }
            logger.error("Erro ao criar painel: {}", e.getMessage(), e);
            return new ResponseEntity<>("Erro ao criar painel: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public java.util.List<Painel> listarPainel() {
        return painelRepository.findAll();
    }
}
