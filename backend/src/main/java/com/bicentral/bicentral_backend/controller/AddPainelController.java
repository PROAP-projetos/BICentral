package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus; // Importar HttpStatus
import org.springframework.http.ResponseEntity; // Importar ResponseEntity
import java.util.Optional; // Importar Optional (necessário para o findBy)

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    @Autowired
    private AddPainelRepository addPainelRepository;

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

        // 3. Se o link for único, salva o novo painel.
        AddPainel painelSalvo = addPainelRepository.save(novoPainel);

        // Retorna o status HTTP 201 Created e o objeto salvo.
        return new ResponseEntity<>(painelSalvo, HttpStatus.CREATED);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        return addPainelRepository.findAll();
    }
}