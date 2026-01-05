package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.AddPainel;
import com.bicentral.bicentral_backend.repository.AddPainelRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(PowerBIScraperService.class);

    @Autowired
    private AddPainelRepository addPainelRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    /**
     * Captura o screenshot de um painel Power BI e salva como arquivo local temporário
     */
    public Path capturarScreenshotComoArquivo(String urlPainel) {
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
            TimeUnit.SECONDS.sleep(10);

            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path destino = Path.of("temp_" + System.currentTimeMillis() + ".png");
            Files.copy(srcFile.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

            return destino;
        } catch (Exception e) {
            logger.error("Erro ao capturar screenshot do painel: {}", urlPainel, e);
            return null;
        } finally {
            driver.quit();
        }
    }

    /**
     * Método assíncrono para capturar capa de um painel e atualizar no banco
     */
    @Async("taskExecutor")
    public void capturaCapaAsync(Long painelId) {
        Path arquivo = null;
        try {
            AddPainel painel = addPainelRepository.findById(painelId)
                    .orElseThrow(() -> new RuntimeException("Painel não encontrado com ID: " + painelId));

            painel.setStatusCaptura(AddPainel.StatusCaptura.PROCESSANDO);
            painel.setDataUltimaCaptura(LocalDateTime.now());
            addPainelRepository.save(painel);

            logger.info("Capturando screenshot do painel: {}", painel.getNome());

            arquivo = capturarScreenshotComoArquivo(painel.getLinkPowerBi());

            if (arquivo == null) {
                painel.setStatusCaptura(AddPainel.StatusCaptura.ERRO);
                addPainelRepository.save(painel);
                return;
            }

            String nomeArquivo = "paineis/" + painel.getId() + ".png";
            String urlFinal = supabaseStorageService.uploadFile(nomeArquivo, arquivo);

            painel.setImagemCapaUrl(urlFinal);
            painel.setStatusCaptura(AddPainel.StatusCaptura.CONCLUIDA);
            painel.setDataUltimaCaptura(LocalDateTime.now());
            addPainelRepository.save(painel);

            logger.info("Upload concluído para painel {}: {}", painel.getNome(), urlFinal);

        } catch (Exception e) {
            logger.error("Erro durante captura assíncrona do painel ID: {}", painelId, e);

            try {
                AddPainel painel = addPainelRepository.findById(painelId).orElse(null);
                if (painel != null) {
                    painel.setStatusCaptura(AddPainel.StatusCaptura.ERRO);
                    addPainelRepository.save(painel);
                }
            } catch (Exception ex) {
                logger.error("Erro ao atualizar status de erro do painel ID: {}", painelId, ex);
            }
        } finally {
            if (arquivo != null) {
                try {
                    Files.deleteIfExists(arquivo);
                    logger.debug("Arquivo temporário deletado: {}", arquivo);
                } catch (Exception e) {
                    logger.error("Erro ao deletar arquivo temporário: {}", arquivo, e);
                }
            }
        }
    }
}
