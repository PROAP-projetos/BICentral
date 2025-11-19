package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class PowerBIScraperService {

    @Autowired
    private AddPainelRepository addPainelRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    /**
     * Captura o screenshot de um painel Power BI e salva como arquivo local temporário
     */
    public Path capturarScreenshotComoArquivo(String urlPainel, String nomePainel) {

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        try {
            driver.get(urlPainel);

            // Aguarda carregamento do PowerBI
            TimeUnit.SECONDS.sleep(10);

            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

            // Criar arquivo temporário
            Path destino = Path.of("temp_" + System.currentTimeMillis() + ".png");
            Files.copy(srcFile.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

            return destino;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            driver.quit();
        }
    }

    /**
     * Método assíncrono para capturar capa de um painel e atualizar no banco (URL, não Base64)
     */
    @Async("taskExecutor")
    public void capturaCapaAsync(Long painelId) {
        try {
            // 1. Busca o painel
            AddPainel painel = addPainelRepository.findById(painelId)
                    .orElseThrow(() -> new RuntimeException("Painel não encontrado com ID: " + painelId));

            painel.setStatusCaptura(AddPainel.StatusCaptura.PROCESSANDO);
            painel.setDataUltimaCaptura(LocalDateTime.now());
            addPainelRepository.save(painel);

            System.out.println("Capturando screenshot do painel: " + painel.getNome());

            // 2. Screenshot → arquivo
            Path arquivo = capturarScreenshotComoArquivo(painel.getLinkPowerBi(), painel.getNome());

            if (arquivo == null) {
                painel.setStatusCaptura(AddPainel.StatusCaptura.ERRO);
                addPainelRepository.save(painel);
                return;
            }

            // 3. Upload no Supabase
            String nomeArquivo = "paineis/" + painel.getId() + ".png";
            String urlFinal = supabaseStorageService.uploadFile(nomeArquivo, arquivo);

            // 4. Atualizar painel com URL final
            painel.setImagemCapaUrl(urlFinal);
            painel.setStatusCaptura(AddPainel.StatusCaptura.CONCLUIDA);
            painel.setDataUltimaCaptura(LocalDateTime.now());
            addPainelRepository.save(painel);

            System.out.println("Upload concluído: " + urlFinal);

        } catch (Exception e) {

            System.err.println("Erro durante captura assíncrona: " + e.getMessage());
            e.printStackTrace();

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
