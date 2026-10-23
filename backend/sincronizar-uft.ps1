# Sincronização manual de PAT/Tarefas da API da UFT — roda em 3 fases porque a VPN da UFT
# (GlobalProtect) e o acesso ao banco (Supabase, via Cloudflare WARP) não funcionam ao mesmo
# tempo nesta máquina. O GlobalProtect não tem CLI conhecido, então essa etapa fica manual —
# o script para e espera você conectar/desconectar na bandeja do sistema.
#
# Se você já estiver na rede da UFT (ex: wifi do campus), NÃO precisa ligar o GlobalProtect —
# só aperte ENTER direto quando o script pedir, o acesso à API já funciona sem VPN nesse caso.
#
# IMPORTANTE: por causa da etapa manual, este script não roda 100% sozinho em segundo plano.
# Rode-o você mesma (duplo clique, ou num terminal) quando tiver um minuto — ou agende no
# Agendador de Tarefas do Windows como "executar somente com o usuário conectado", pra abrir
# como janela visível na hora agendada em vez de tentar rodar escondido.

$ErrorActionPreference = "Stop"
$Jar = Join-Path $PSScriptRoot "target\bicentral-backend-0.0.1-SNAPSHOT.jar"
$Warp = "C:\Program Files\Cloudflare\Cloudflare WARP\warp-cli.exe"
$MainClass = "com.bicentral.bicentral_backend.job.SincronizacaoUftApplication"

function Log($msg) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $msg"
}

# Sempre pelo ponto de entrada enxuto (SincronizacaoUftApplication), nunca "java -jar" direto —
# esse último sobe o app COMPLETO (segurança, IA, todos os services de admin), e vários desses
# beans tentam conexão de banco já na inicialização, o que quebra a fase "fetch" (sem banco
# disponível de propósito). Ver SincronizacaoUftApplication.java para o motivo completo.
function Rodar-Fase($fase) {
    Log "Rodando fase '$fase'..."
    & java "-Dloader.main=$MainClass" -cp $Jar org.springframework.boot.loader.launch.PropertiesLauncher `
        --spring.profiles.active=sync-uft "--sync.fase=$fase"
    if ($LASTEXITCODE -ne 0) {
        Log "AVISO: fase '$fase' terminou com codigo de saida $LASTEXITCODE."
    }
}

Log "=== Sincronizacao UFT - inicio ==="

# --- Fase 1/3: config (precisa do banco -> Cloudflare WARP ligado) ---
Log "Conectando Cloudflare WARP..."
& $Warp connect | Out-Null
Start-Sleep -Seconds 5
Rodar-Fase "config"

# --- Fase 2/3: fetch (precisa da VPN da UFT -> GlobalProtect, manual) ---
Log "Desconectando Cloudflare WARP..."
& $Warp disconnect | Out-Null

Write-Host ""
Write-Host "=== ACAO MANUAL (se precisar) ===" -ForegroundColor Yellow
Write-Host "Se voce NAO estiver na rede da UFT agora, conecte o GlobalProtect pela bandeja do sistema." -ForegroundColor Yellow
Write-Host "Se ja estiver na rede da UFT (ex: wifi do campus), pode so apertar ENTER direto." -ForegroundColor Yellow
Read-Host "Pressione ENTER quando estiver pronto (GlobalProtect conectado, ou ja na rede da UFT)"

Rodar-Fase "fetch"

Write-Host ""
Write-Host "=== ACAO MANUAL (se precisar) ===" -ForegroundColor Yellow
Write-Host "Se conectou o GlobalProtect na etapa anterior, desconecte agora." -ForegroundColor Yellow
Read-Host "Pressione ENTER para continuar"

# --- Fase 3/3: load (precisa do banco de novo -> Cloudflare WARP ligado) ---
Log "Conectando Cloudflare WARP..."
& $Warp connect | Out-Null
Start-Sleep -Seconds 5
Rodar-Fase "load"

Log "=== Sincronizacao UFT - concluida ==="
Read-Host "Pressione ENTER para fechar"
