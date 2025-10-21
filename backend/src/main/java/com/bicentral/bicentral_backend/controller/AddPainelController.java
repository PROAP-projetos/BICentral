package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "http://localhost:4200")
public class AddPainelController {

    @Autowired
    private AddPainelRepository addPainelRepository;

    @PostMapping
    public AddPainel criarPainel(@RequestBody AddPainel novoPainel) {
        return addPainelRepository.save(novoPainel);
    }

    @GetMapping
    public java.util.List<AddPainel> listarPainel() {
        return addPainelRepository.findAll();
    }
}