package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/painel")
// CORREÇÃO AQUI: Mude a porta para 4200 (onde o Angular está rodando)
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    @Autowired
    // CORREÇÃO AQUI: Variáveis de instância DEVEM começar com letra minúscula (convenção Java/Spring)
    private AddPainelRepository addPainelRepository;

    @PostMapping
    public AddPainel criarPainel(@RequestBody AddPainel novoPainel) {
        // Use a variável corrigida
        return addPainelRepository.save(novoPainel);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        // Use a variável corrigida
        return addPainelRepository.findAll();
    }
}