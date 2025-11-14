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

    /**
     * Endpoint simples para testar se a API está funcionando
     */
    @GetMapping("/teste")
    public ResponseEntity<String> teste() {
        return ResponseEntity.ok("API funcionando!");
    }

    /**
     * NOVO ENDPOINT DE TESTE: Foca em um único painel fixo (hardcoded) para testar 
     * a funcionalidade do scraper isoladamente.
     */
    @GetMapping("/teste-scraper")
    public ResponseEntity<PainelDTO> testScraperSingleDashboard() {

        // DADOS FIXOS PARA TESTE (Hardcoded)
        final String URL_PAINEL = "https://app.powerbi.com/view?r=eyJrIjoiNjg5ZDVlNWMtMzY3MS00YWY3LTg0MzQtNjIzMzVlZWYzODEyIiwidCI6ImNkNTFhOTYzLWRiNDctNDhlZi05ZDFjLWU4MzIzYjYyYTQzMSJ9";
        final String NOME_PAINEL = "Painel de Efetividade";

        System.out.println("Iniciando teste de web scraping para: " + NOME_PAINEL);

        // 1. Chama o serviço de web scraping
        String imagemBase64 = scraperService.capturarCapaEmBase64(
            URL_PAINEL, 
            NOME_PAINEL
        );

        // 2. Monta o DTO de resposta
        PainelDTO dto = new PainelDTO();
        dto.setNome(NOME_PAINEL);
        dto.setLinkPowerBi(URL_PAINEL);
        dto.setImagemCapaBase64(imagemBase64); 

        // 3. Verifica o resultado
        if (imagemBase64 == null) {
             System.err.println("Falha no web scraping. Verifique logs do Selenium/ChromeDriver.");
             // Retorna status 500 em caso de falha de captura
             return ResponseEntity.status(500).body(dto); 
        }

        System.out.println("Web scraping concluído com sucesso.");
        return ResponseEntity.ok(dto);
    }

    /**
     * Endpoint principal. Busca a lista de painéis no banco e retorna as capas 
     * já armazenadas (carregamento instantâneo).
     */
    @GetMapping("/com-capa")
    public ResponseEntity<List<PainelDTO>> getAllPaineisComCapa() {
        try {
            // 1. Busca todos os painéis do banco (Entidade AddPainel).
            List<AddPainel> paineis = addPainelRepository.findAll(); 
            
            // 2. Inicializa a lista de retorno como PainelDTO
            List<PainelDTO> resultado = new ArrayList<>(); 

            for (AddPainel painel : paineis) {
                // 3. Mapeia a Entidade para o DTO (incluindo a imagem Base64 já armazenada)
                PainelDTO dto = new PainelDTO();
                dto.setNome(painel.getNome());
                dto.setLinkPowerBi(painel.getLinkPowerBi());
                dto.setImagemCapaBase64(painel.getImagemCapaBase64());
                dto.setStatusCaptura(painel.getStatusCaptura());
                
                resultado.add(dto);
            }
            
            // 4. Retorna o resultado (carregamento instantâneo!)
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            System.err.println("Erro no endpoint /com-capa: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    /**
     * Endpoint para atualizar manualmente a capa de um painel específico
     */
    @PostMapping("/atualizar-capa/{id}")
    public ResponseEntity<String> atualizarCapa(@PathVariable Long id) {
        try {
            // Verifica se o painel existe
            if (!addPainelRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            // Inicia captura assíncrona
            scraperService.capturaCapaAsync(id);
            
            return ResponseEntity.ok("Atualização de capa iniciada para painel ID: " + id);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar atualização de capa: " + e.getMessage());
            return ResponseEntity.status(500).body("Erro ao iniciar atualização de capa");
        }
    }
    
}