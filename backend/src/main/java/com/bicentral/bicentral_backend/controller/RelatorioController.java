package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.service.RelatorioService;
import com.bicentral.bicentral_backend.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proiap/relatorio")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final UsuarioService usuarioService;
    private final JdbcTemplate jdbcTemplate;

    public RelatorioController(RelatorioService relatorioService, UsuarioService usuarioService, JdbcTemplate jdbcTemplate) {
        this.relatorioService = relatorioService;
        this.usuarioService = usuarioService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SolicitarRelatorioRequest(String departamento, String tipo) {}

    @GetMapping("/departamentos")
    public ResponseEntity<List<String>> listarDepartamentos() {
        List<String> departamentos = jdbcTemplate.queryForList(
            "SELECT DISTINCT departamento FROM pat_execucao_departamento ORDER BY departamento",
            String.class
        );
        return ResponseEntity.ok(departamentos);
    }

    @PostMapping("/gerar")
    public ResponseEntity<Map<String, Object>> gerar(
            @RequestBody SolicitarRelatorioRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        Long id = relatorioService.solicitarRelatorio(usuario.getId(), request.departamento(), request.tipo());

        return ResponseEntity.accepted().body(Map.of(
            "id", id,
            "status", "PROCESSANDO",
            "mensagem", "Relatório sendo gerado. Consulte o status em alguns segundos."
        ));
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id) {
        return ResponseEntity.ok(relatorioService.buscarStatus(id));
    }
}