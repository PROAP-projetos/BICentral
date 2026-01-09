package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
public class PowerBIScraperService {

    private static final Logger logger = LoggerFactory.getLogger(PowerBIScraperService.class);

    @Autowired
    private PainelRepository painelRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    /**
     * Executa a captura em segundo plano.
     * Atualiza o banco para o Angular Polling funcionar.
     */
    @Async("taskExecutor")
    public void capturaCapaAsync(Long painelId) {
        Path tempFile = null;
        try {
            Painel painel = painelRepository.findById(painelId)
                    .orElseThrow(() -> new RuntimeException("Painel não encontrado ID: " + painelId));

            // 1. Marca como processando para o Angular mostrar "Gerando capa..."
            painel.setStatusCaptura(Painel.StatusCaptura.PROCESSANDO);
            painelRepository.save(painel);

            logger.info("Playwright: Iniciando captura para {}", painel.getNome());

            // 2. Inicia o Playwright
            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
                Page page = context.newPage();

                page.navigate(painel.getLinkPowerBi());

                logger.info("Aguardando carregamento do dashboard...");
                page.waitForSelector(".visualContainerHost", new Page.WaitForSelectorOptions().setTimeout(60000));

                // Pequena pausa para garantir que os dados do gráfico carreguem
                page.waitForTimeout(3000);

                tempFile = Files.createTempFile("pbi-", ".png");
                page.screenshot(new Page.ScreenshotOptions().setPath(tempFile));

                String nomeArquivo = "paineis/" + painel.getId() + ".png";
                String urlFinal = supabaseStorageService.uploadFile(nomeArquivo, tempFile);

                painel.setImagemCapaUrl(urlFinal);
                painel.setStatusCaptura(Painel.StatusCaptura.CONCLUIDA);
                painel.setDataUltimaCaptura(LocalDateTime.now());
                painelRepository.save(painel);

                logger.info("Playwright: Sucesso total para {}", painel.getNome());
            }

        } catch (Exception e) {
            logger.error("Erro no Scraper Playwright: ", e);
            marcarComoErro(painelId);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    private void marcarComoErro(Long painelId) {
        painelRepository.findById(painelId).ifPresent(p -> {
            p.setStatusCaptura(Painel.StatusCaptura.ERRO);
            painelRepository.save(p);
        });
    }
}