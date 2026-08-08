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

        try {
            Files.lines(Paths.get("..env")).forEach(line -> {
                if (line.contains("=") && !line.startsWith("#")) {
                    String[] parts = line.split("=", 2);
                    envMap.put(parts[0].trim(), parts[1].trim());
                }
            });
        } catch (IOException e) {
            System.out.println("⚠ ..env não encontrado (ignorando)");
        }

        ConfigurableEnvironment environment = event.getEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("customDotenv", envMap)
        );
    }
}
