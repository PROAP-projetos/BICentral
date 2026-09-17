# Jobs do backend

Três `@Component` com `@Scheduled` rodam automaticamente enquanto o backend está no ar
(local ou no Render). Esta página explica pra que cada um serve e — o mais importante —
como manter os dados da UFT atualizados de verdade, já que a sincronização automática
**não funciona em produção** hoje.

## Os 3 jobs

| Job | Arquivo | O que faz | Roda sozinho em produção? |
|---|---|---|---|
| Sincronização do PAT | `job/SincronizacaoPatJob.java` | Busca o PAT (ano corrente) na API da UFT e grava em `pat_dados` | **Não** — ver seção abaixo |
| Sincronização de Tarefas | `job/SincronizacaoTarefasJob.java` | Busca as tarefas do PAT na API da UFT e grava em `pat_tarefas` | **Não** — ver seção abaixo |
| Snapshot de ranking | `job/RankingSnapshotJob.java` | Tira uma "foto" diária da execução por departamento (`pat_execucao_departamento`) e salva em `ranking_pat_snapshots`, pra dar histórico de tendência | Sim — não chama a API da UFT, só lê o que já está no banco |

Os 3 têm `@Scheduled(fixedRate = 24h)` com `initialDelay` de 60-100s — ou seja, tentam
rodar sozinhos uma vez por dia a partir de quando o processo sobe (não é um horário fixo
do relógio, é 24h depois da última vez que o backend reiniciou).

## Por que a sincronização automática do PAT/Tarefas não funciona sozinha

A API da UFT (`api.uft.edu.br`) só aceita conexão de **dentro da rede da UFT** — confirmado
em 2026-09-17 testando de três lugares: do Render (produção) deu erro de conexão; de fora
da UFT com uma ferramenta de rede externa a API respondeu normalmente (404/401 — ou seja,
o servidor está de pé, só não deixa qualquer um entrar); e da rede/wifi da UFT funcionou
com dado real. Isso quer dizer que **o Render nunca vai conseguir rodar esses dois jobs
sozinho** — ele não tem como estar "dentro" da rede da UFT. O job de snapshot de ranking
não tem esse problema porque não chama a API da UFT, só lê dado que já está no banco.

Enquanto isso não muda (só mudaria se a UFT liberasse o IP do Render, ou o Render desse
IP fixo pra cadastrar lá — nenhuma das duas depende de nós), quem mantém o PAT/Tarefas
atualizado é a sincronização **manual**, abaixo.

## Como atualizar manualmente

Pré-requisitos (só na primeira vez): estar com o projeto compilado
(`.\mvnw package -DskipTests`, gera o `.jar` em `target/`) e ter o Cloudflare WARP
instalado (usado pra alcançar o banco Supabase).

1. Abra um terminal em `backend/`.
2. Rode:
   ```
   .\sincronizar-uft.ps1
   ```
3. O script faz sozinho: liga o Cloudflare WARP, lê a configuração da API (URL/token) do
   banco, desliga o Cloudflare WARP, e para pra você numa etapa manual.
4. Nessa etapa manual:
   - Se você **não estiver** na rede da UFT agora, conecte o GlobalProtect (VPN da UFT)
     pela bandeja do sistema antes de apertar ENTER.
   - Se você **já estiver** na rede da UFT (ex: wifi do campus), não precisa ligar nada —
     só aperte ENTER direto.
5. O script busca os dados da API da UFT e salva localmente (ainda não grava no banco).
6. Ele pede pra você desconectar o GlobalProtect (se tiver ligado) e aperta ENTER de novo.
7. Ele liga o Cloudflare WARP sozinho de novo e grava tudo no banco — PAT e Tarefas.
8. No final, mostra quantos registros foram carregados de cada um.

Isso é idempotente — pode rodar de novo quantas vezes quiser no mesmo dia sem risco de
duplicar nada (usa `ON CONFLICT ... DO UPDATE`).

**Frequência recomendada**: pelo menos uma vez por semana; idealmente sempre que for
mexer em algo que dependa de dado fresco do PAT/Tarefas (ex: mostrar pros testers,
gerar relatório importante).

### Rodando uma fase manualmente (avançado)

Se precisar debugar ou rodar só um pedaço, cada fase pode ser chamada direto (sempre pelo
ponto de entrada enxuto, nunca `java -jar` direto — ver comentário em
`SincronizacaoUftApplication.java` pro motivo):

```
java "-Dloader.main=com.bicentral.bicentral_backend.job.SincronizacaoUftApplication" `
  -cp target\bicentral-backend-0.0.1-SNAPSHOT.jar `
  org.springframework.boot.loader.launch.PropertiesLauncher `
  --spring.profiles.active=sync-uft --sync.fase=config
```

Troque `config` por `fetch` ou `load`. A ordem importa: `config` precisa rodar antes de
`fetch`, que precisa rodar antes de `load`.

## Onde ver o status da última tentativa

`/admin` → seção "Integrações UFT (APIs)" mostra o último status (`SUCESSO`/`ERRO`) e a
mensagem de cada API, incluindo as tentativas automáticas (sempre com erro) que o Render
continua fazendo sozinho todo dia — isso é esperado, não é motivo de alarme.
