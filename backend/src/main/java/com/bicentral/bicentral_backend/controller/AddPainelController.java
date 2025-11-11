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
        // 1. Buscar no repositório se já existe um painel com este link
        Optional<AddPainel> painelExistente = addPainelRepository.findByLinkPowerBi(novoPainel.getLinkPowerBi());

        // 2. Se o Optional não estiver vazio, significa que o link já existe.
        if (painelExistente.isPresent()) {
            // Retorna o status HTTP 409 Conflict e a mensagem de erro.
            String mensagemErro = "Painel já cadastrado";
            return new ResponseEntity<>(mensagemErro, HttpStatus.CONFLICT);
        }

        // 3. Garantir que o status inicial seja PENDENTE
        novoPainel.setStatusCaptura(AddPainel.StatusCaptura.PENDENTE);
        novoPainel.setImagemCapaBase64(null);

        // 4. Salva o novo painel
        AddPainel painelSalvo = addPainelRepository.save(novoPainel);

        // 5. Inicia captura assíncrona da capa
        try {
            scraperService.capturaCapaAsync(painelSalvo.getId());
            System.out.println("Captura assíncrona iniciada para painel ID: " + painelSalvo.getId());
        } catch (Exception e) {
            System.err.println("Erro ao iniciar captura assíncrona: " + e.getMessage());
            // Não falha a criação do painel, apenas loga o erro
        }

        // 6. Retorna o status HTTP 201 Created e o objeto salvo
        return new ResponseEntity<>(painelSalvo, HttpStatus.CREATED);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        return addPainelRepository.findAll();
    }
}