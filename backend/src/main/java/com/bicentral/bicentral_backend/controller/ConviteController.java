package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.AceiteConviteRequestDTO;
import com.bicentral.bicentral_backend.dto.AceiteConviteResponseDTO;
import com.bicentral.bicentral_backend.service.ConviteEquipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/convites")
public class ConviteController {

    private final ConviteEquipeService conviteEquipeService;

    public ConviteController(ConviteEquipeService conviteEquipeService) {
        this.conviteEquipeService = conviteEquipeService;
    }

    @PostMapping("/aceitar")
    public ResponseEntity<AceiteConviteResponseDTO> aceitarConvite(@Valid @RequestBody AceiteConviteRequestDTO dto) {
        return ResponseEntity.ok(conviteEquipeService.aceitarConvite(dto.token()));
    }
}
