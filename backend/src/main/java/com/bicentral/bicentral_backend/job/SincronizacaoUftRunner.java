package com.bicentral.bicentral_backend.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Modo "roda e sai", pra sincronizar PAT e Tarefas manualmente de uma máquina sem acesso
// simultâneo à rede da UFT (API) e ao banco (Supabase) — só ativa com
// --spring.profiles.active=sync-uft, nunca roda junto do backend normal.
//
// Precisa de 3 execuções separadas, uma por fase (--sync.fase=config|fetch|load), porque a VPN
// da UFT e o acesso ao Supabase não funcionam ao mesmo tempo nessa máquina (ver comentário em
// SincronizacaoPatJob.PASTA_PONTE): "config" e "load" precisam do banco (VPN desligada),
// "fetch" precisa da VPN ligada (sem banco). Quem orquestra a troca de rede entre as fases é o
// script sincronizar-uft.ps1, não esta classe.
@Component
@Profile("sync-uft")
public class SincronizacaoUftRunner implements CommandLineRunner {

    private final SincronizacaoPatJob patJob;
    private final SincronizacaoTarefasJob tarefasJob;
    private final ConfigurableApplicationContext contexto;

    @Value("${sync.fase:}")
    private String fase;

    public SincronizacaoUftRunner(SincronizacaoPatJob patJob, SincronizacaoTarefasJob tarefasJob,
            ConfigurableApplicationContext contexto) {
        this.patJob = patJob;
        this.tarefasJob = tarefasJob;
        this.contexto = contexto;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> SYNC-UFT: fase '" + fase + "'");

        switch (fase) {
            case "config" -> {
                patJob.salvarConfiguracaoLocal();
                tarefasJob.salvarConfiguracaoLocal();
            }
            case "fetch" -> {
                patJob.buscarDaUftEsalvarLocal();
                tarefasJob.buscarDaUftEsalvarLocal();
            }
            case "load" -> {
                patJob.carregarNoBanco();
                tarefasJob.carregarNoBanco();
            }
            default -> System.err.println(
                    ">>> SYNC-UFT: fase inválida ou não informada. Use --sync.fase=config, fetch ou load.");
        }

        System.out.println(">>> SYNC-UFT: concluído, encerrando.");
        System.exit(SpringApplication.exit(contexto, () -> 0));
    }
}
