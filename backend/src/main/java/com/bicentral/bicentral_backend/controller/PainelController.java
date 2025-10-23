package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.PainelDTO;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.service.PainelService;
import com.bicentral.bicentral_backend.service.PowerBIScraperService; 

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/paineis")
@CrossOrigin(origins = "http://localhost:4200")
public class PainelController {

    private final PainelService painelService;
    private final PowerBIScraperService scraperService;

    public PainelController(PainelService painelService, PowerBIScraperService scraperService) {
        this.painelService = painelService;
        this.scraperService = scraperService;
    }

    /**
     * Endpoint principal. Busca a lista de painéis no banco e chama o scraper 
     * para obter a capa de cada um.
     */
    @GetMapping("/com-capa")
    public ResponseEntity<List<PainelDTO>> getAllPaineisComCapa() {
        
        // 1. Busca todos os painéis do banco (Entidade Painel).
        List<Painel> paineis = painelService.findAll(); 
        
        // 2. Inicializa a lista de retorno como PainelDTO
        List<PainelDTO> resultado = new ArrayList<>(); 

        for (Painel painel : paineis) {
            
            // Chama o serviço de web scraping
            String imagemBase64 = scraperService.capturarCapaEmBase64(
                painel.getLinkPowerBi(), 
                painel.getNome()
            );

            // 3. Mapeia a Entidade para o DTO (incluindo a imagem Base64)
            PainelDTO dto = new PainelDTO();
            dto.setNome(painel.getNome());
            dto.setLinkPowerBi(painel.getLinkPowerBi());
            
            // Usa o setter do DTO
            dto.setImagemCapaBase64(imagemBase64); 
            
            resultado.add(dto);
        }
        
        // 4. Retorna o resultado
        return ResponseEntity.ok(resultado);
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
}