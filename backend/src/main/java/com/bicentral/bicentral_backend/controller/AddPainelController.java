package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import com.bicentral.bicentral_backend.service.PowerBIScraperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Optional;

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    @Autowired
    private AddPainelRepository addPainelRepository;

    @Autowired
    private PowerBIScraperService scraperService;

    @PostMapping
    public ResponseEntity<?> criarPainel(@RequestBody AddPainel novoPainel) {
        // 1. Verifica se já existe painel com o mesmo link
        Optional<AddPainel> painelExistente = addPainelRepository.findByLinkPowerBi(novoPainel.getLinkPowerBi());

        if (painelExistente.isPresent()) {
            return new ResponseEntity<>("Painel já cadastrado", HttpStatus.CONFLICT);
        }

        // 2. Define status inicial PENDENTE
        novoPainel.setStatusCaptura(AddPainel.StatusCaptura.PENDENTE);

        // 3. Agora o nome correto do campo é imagemCapaUrl
        novoPainel.setImagemCapaUrl(null);

        // 4. Salva o novo painel
        AddPainel painelSalvo = addPainelRepository.save(novoPainel);

        // 5. Inicia captura assíncrona
        try {
            scraperService.capturaCapaAsync(painelSalvo.getId());
        } catch (Exception e) {
            System.err.println("Erro ao iniciar captura assíncrona: " + e.getMessage());
        }

        return new ResponseEntity<>(painelSalvo, HttpStatus.CREATED);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        return addPainelRepository.findAll();
    }
}
