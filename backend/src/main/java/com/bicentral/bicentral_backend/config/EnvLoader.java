package com.bicentral.bicentral_backend.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(@NonNull ApplicationEnvironmentPreparedEvent event) {
        Map<String, Object> envMap = new HashMap<>();

        // Tenta carregar o .env do diretório raiz ou do diretório atual
        String[] possiblePaths = {"./.env", "./backend/.env", "../.env"};
        
        for (String pathStr : possiblePaths) {
            java.nio.file.Path path = Paths.get(pathStr);
            if (Files.exists(path)) {
                try {
                    Files.lines(path).forEach(line -> {
                        if (line.contains("=") && !line.startsWith("#")) {
                            String[] parts = line.split("=", 2);
                            envMap.put(parts[0].trim(), parts[1].trim());
                        }
                    });
                    System.out.println("✅ .env carregado de: " + path.toAbsolutePath());
                    break; // Para no primeiro que encontrar
                } catch (IOException e) {
                    System.err.println("⚠ Erro ao ler " + pathStr + ": " + e.getMessage());
                }
            } else {
                System.out.println("🔎 " + pathStr + " não encontrado.");
            }
        }
        if (envMap.isEmpty()) {
            System.err.println("❌ Nenhum arquivo .env foi encontrado ou carregado.");
        }

        ConfigurableEnvironment environment = event.getEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("customDotenv", envMap)
        );
    }
}
