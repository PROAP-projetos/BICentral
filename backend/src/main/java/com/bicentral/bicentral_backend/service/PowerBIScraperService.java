package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class PowerBIScraperService {

    private final String IMAGES_PATH = "storage/paineis/"; // Local para salvar as imagens
    
    // ATENÇÃO: Use variáveis de ambiente ou Vault para credenciais.
    private final String PBI_USERNAME = "dallyla.moraes@mail.uft.edu.br";
    private final String PBI_PASSWORD = "Beng@123";

    @Autowired
    private AddPainelRepository addPainelRepository;

    public String capturarCapaEmBase64(String urlPainel, String nomePainel) {
        
        // 1. Configuração do Driver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");        // Modo sem GUI
        options.addArguments("--no-sandbox");      // Necessário para alguns ambientes (ex: Docker)
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080"); // Define o tamanho da tela para a captura

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        try {
            driver.get(urlPainel);
            
            // ******************************************************
            // 2. O PONTO CRÍTICO: AUTENTICAÇÃO
            // ESTE CÓDIGO É ALTAMENTE FRÁGIL E DEPENDENTE DA TELA DE LOGIN DO MS/AZURE AD.
            // ******************************************************
            
            System.out.println("Tentando autenticar...");
            
            // Exemplo de Autenticação Fase 1: Inserir e-mail
            // Pode precisar ajustar o seletor (ID ou nome)
           // driver.findElement(By.id("i0116")).sendKeys(PBI_USERNAME); 
           // driver.findElement(By.id("idSIButton9")).click();
            
            // Espera a próxima tela (senha) carregar
            TimeUnit.SECONDS.sleep(3);
            
            // Exemplo de Autenticação Fase 2: Inserir senha
         //   driver.findElement(By.id("i0118")).sendKeys(PBI_PASSWORD);
         //   driver.findElement(By.id("idSIButton9")).click(); 
            
            // Esperar o PBI carregar a página principal e os visuais
            TimeUnit.SECONDS.sleep(10); // Dê tempo para os scripts do Power BI rodarem

            // Verifique se a página carregou procurando um elemento comum do PBI (opcional)
            // if (driver.findElements(By.cssSelector("seletor-de-um-elemento-do-powerbi")).isEmpty()) {
            //     throw new RuntimeException("Falha ao carregar o dashboard do Power BI após login.");
            // }

            // 3. Captura de Tela do Elemento Principal (Assumindo que o corpo é o dashboard)
            File srcFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);

            // 4. Converte a imagem para Base64 (Para enviar diretamente na sua API)
            byte[] fileContent = Files.readAllBytes(srcFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(fileContent);
            
            System.out.println("Captura bem-sucedida.");
            return base64Image;

        } catch (Exception e) {
            System.err.println("Erro durante a raspagem de tela: " + e.getMessage());
            e.printStackTrace();
            return null; // Retorna nulo em caso de falha
        } finally {
            if (driver != null) {
                driver.quit(); // Sempre feche o navegador!
            }
        }
    }

    /**
     * Método assíncrono para capturar capa de um painel e atualizar no banco
     * @param painelId ID do painel a ser processado
     */
    @Async("taskExecutor")
    public void capturaCapaAsync(Long painelId) {
        try {
            // 1. Busca o painel no banco
            AddPainel painel = addPainelRepository.findById(painelId)
                .orElseThrow(() -> new RuntimeException("Painel não encontrado com ID: " + painelId));

            // 2. Atualiza status para PROCESSANDO
            painel.setStatusCaptura(AddPainel.StatusCaptura.PROCESSANDO);
            painel.setDataUltimaCaptura(LocalDateTime.now());
            addPainelRepository.save(painel);

            System.out.println("Iniciando captura assíncrona para painel: " + painel.getNome());

            // 3. Executa a captura
            String imagemBase64 = capturarCapaEmBase64(painel.getLinkPowerBi(), painel.getNome());

            // 4. Atualiza o painel com o resultado
            if (imagemBase64 != null) {
                painel.setImagemCapaBase64(imagemBase64);
                painel.setStatusCaptura(AddPainel.StatusCaptura.CONCLUIDA);
                System.out.println("Captura concluída com sucesso para painel: " + painel.getNome());
            } else {
                painel.setStatusCaptura(AddPainel.StatusCaptura.ERRO);
                System.err.println("Falha na captura para painel: " + painel.getNome());
            }

            addPainelRepository.save(painel);

        } catch (Exception e) {
            System.err.println("Erro durante captura assíncrona: " + e.getMessage());
            e.printStackTrace();
            
            // Atualiza status para ERRO
            try {
                AddPainel painel = addPainelRepository.findById(painelId).orElse(null);
                if (painel != null) {
                    painel.setStatusCaptura(AddPainel.StatusCaptura.ERRO);
                    addPainelRepository.save(painel);
                }
            } catch (Exception ex) {
                System.err.println("Erro ao atualizar status de erro: " + ex.getMessage());
            }
        }
    }
}