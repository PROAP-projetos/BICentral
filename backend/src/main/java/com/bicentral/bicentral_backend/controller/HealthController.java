package com.bicentral.bicentral_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Endpoint público e leve (sem tocar em banco/IA) só pra um ping externo (cron-job.org,
// UptimeRobot etc.) manter o serviço acordado no Render free tier — sem isso ele dorme
// após ~15min sem uso e a primeira pergunta do dia falha/demora até o backend religar.
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
