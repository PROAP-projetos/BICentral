package com.bicentral.bicentral_backend.service;

import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PowerBIScraperService {

    private static final Logger logger = LoggerFactory.getLogger(PowerBIScraperService.class);
    private static final String PREFIXO_POWER_BI = "https://app.powerbi.com/view?r=";

    // Timeouts (ms)
    private static final double NAV_TIMEOUT = 60_000;
    private static final double WAIT_TIMEOUT = 60_000;

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
        Long id = Objects.requireNonNull(painelId, "painelId");
        Path tempFile = null;

        try {
            Painel painel = painelRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Painel não encontrado ID: " + id));

            if (!linkPowerBiValido(painel.getLinkPowerBi())) {
                logger.warn("Link Power BI inválido para painel {}. Marcando como erro.", painel.getId());
                marcarComoErro(id);
                return;
            }

            // 1) Marca como processando para o Angular mostrar "Gerando capa..."
            painel.setStatusCaptura(Painel.StatusCaptura.PROCESSANDO);
            painelRepository.save(painel);

            logger.info("Playwright: Iniciando captura para {}", painel.getNome());

            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(
                         new BrowserType.LaunchOptions().setHeadless(true)
                 )) {

                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080)
                );

                Page page = context.newPage();
                page.setDefaultTimeout(WAIT_TIMEOUT);
                page.setDefaultNavigationTimeout(NAV_TIMEOUT);

                // Desativa animações/transições (ajuda nas capas)
                page.addStyleTag(new Page.AddStyleTagOptions().setContent(
                        "*{animation:none!important;transition:none!important;caret-color:transparent!important}"
                ));

                // 2) Navega (report direto)
                page.navigate(painel.getLinkPowerBi(), new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(NAV_TIMEOUT)
                );

                logger.info("Aguardando render REAL do dashboard...");

                // 3) Base do report
                page.waitForSelector(".visualContainerHost",
                        new Page.WaitForSelectorOptions().setTimeout(WAIT_TIMEOUT)
                );

                // 4) Espera "loading" sumir (se existir)
                Locator loading = page.locator(
                        ".loading, .spinner, [role='progressbar'], .busyIndicator, .pbi-loading, .loadingOverlay"
                );

                try {
                    loading.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.HIDDEN)
                            .setTimeout(WAIT_TIMEOUT)
                    );
                } catch (PlaywrightException ignored) {
                    // Se não existir spinner detectável, segue.
                }

                // 5) Sinal forte de pronto: algum visual com conteúdo real (canvas/svg/img) e tamanho válido
                page.waitForFunction(
                        "() => {" +
                                "  const hosts = Array.from(document.querySelectorAll('.visualContainerHost'));" +
                                "  if (!hosts.length) return false;" +
                                "  return hosts.some(h => {" +
                                "    const r = h.getBoundingClientRect();" +
                                "    if (r.width < 50 || r.height < 50) return false;" +
                                "    return !!h.querySelector('canvas,svg,img');" +
                                "  });" +
                                "}",
                        null,
                        new Page.WaitForFunctionOptions().setTimeout(WAIT_TIMEOUT)
                );

                // 6) Folga curta pra estabilizar fontes/linhas finais
                page.waitForTimeout(800);

                // 7) Screenshot
                tempFile = Files.createTempFile("pbi-", ".png");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(tempFile)
                        .setFullPage(false)
                );

                String nomeArquivo = "paineis/" + painel.getId() + ".png";
                String urlFinal = supabaseStorageService.uploadFile(nomeArquivo, tempFile);

                painel.setImagemCapaUrl(urlFinal);
                painel.setStatusCaptura(Painel.StatusCaptura.CONCLUIDA);
                painel.setDataUltimaCaptura(LocalDateTime.now());
                painelRepository.save(painel);

                logger.info("Playwright: Sucesso total para {}", painel.getNome());

                try { context.close(); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            logger.error("Erro no Scraper Playwright: ", e);
            marcarComoErro(id);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    private void marcarComoErro(Long painelId) {
        Long id = Objects.requireNonNull(painelId, "painelId");
        painelRepository.findById(id).ifPresent(p -> {
            p.setStatusCaptura(Painel.StatusCaptura.ERRO);
            painelRepository.save(p);
        });
    }

    private boolean linkPowerBiValido(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        if (!link.startsWith(PREFIXO_POWER_BI)) {
            return false;
        }
        try {
            URI uri = new URI(link);
            return "app.powerbi.com".equalsIgnoreCase(uri.getHost())
                    && "/view".equals(uri.getPath())
                    && uri.getQuery() != null
                    && uri.getQuery().contains("r=");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}

