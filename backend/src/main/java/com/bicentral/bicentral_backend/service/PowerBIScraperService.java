package com.bicentral.bicentral_backend.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class PowerBIScraperService {

    private final String IMAGES_PATH = "storage/paineis/"; // Local para salvar as imagens
    
    // ATENÇÃO: Use variáveis de ambiente ou Vault para credenciais.
    private final String PBI_USERNAME = "dallyla.moraes@mail.uft.edu.br";
    private final String PBI_PASSWORD = "Beng@123";

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
}