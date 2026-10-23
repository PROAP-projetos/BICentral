# Os problemas do deploy no Render — o que quebrou e por quê

**Data**: 04/09

Enquanto o BICentral rodava só em `localhost`, front e back eram "o mesmo lugar" pro navegador. Colocar cada um no Render, em domínios diferentes (`bicentral-frontend.onrender.com` e `bicentral-backend.onrender.com`), destrancou uma sequência de problemas que não existiam antes — nenhum é bug de lógica de negócio, são todos sobre **como dois domínios diferentes conversam entre si** e **como hospedagem de site estático funciona**. Esse documento junta os quatro, na ordem em que apareceram, pra ficar registrado o que era cada um.

---

## Problema 1 — Proxy de `/api/*` não funcionava pra requisições POST

**O que foi tentado primeiro**: fazer o front conversar com o back sem precisar de CORS, usando o arquivo `frontend/public/_redirects` como um proxy transparente — toda chamada pra `/api/*` no front seria redirecionada por trás, no servidor, pro domínio real do backend, e o navegador nunca ficaria sabendo que são domínios diferentes.

**O que quebrou**: funcionava pra `GET`, mas requisição `POST` (login, cadastro, perguntar pro agente...) voltava `200` com o **corpo vazio** — como se a chamada tivesse "sumido" no meio do caminho.

**Correção**: abandonar o proxy. As chamadas do front agora vão **direto pra URL do backend** (`https://bicentral-backend.onrender.com/api/...`), com o navegador sabendo que é um domínio diferente — e por isso dependendo de CORS estar liberado no backend (ver Problema 2). Essa troca de URL é feita automaticamente pelo `auth.interceptor.ts` (só em produção; local continua batendo em `/api/...` relativo).

## Problema 2 — CORS / preflight `OPTIONS` bloqueado por engano

**O que quebrou**: com front e back em domínios diferentes (depois do Problema 1), toda chamada que carrega o token JWT (ou seja, quase todas) faz o navegador mandar antes uma requisição `OPTIONS` de "preflight" — uma pergunta automática do navegador tipo "posso mandar essa requisição de verdade?". Essa pergunta **nunca carrega o header `Authorization`**. O Spring Security, do jeito que estava configurado, exigia login pra praticamente tudo — inclusive pro preflight — então o preflight tomava 401 antes mesmo de chegar na configuração de CORS, e a requisição de verdade nunca saía.

**Correção**: liberar explicitamente qualquer requisição `OPTIONS` no `SecurityConfig` (`.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`), sem tirar a exigência de login do resto.

## Problema 3 — Render "desliga" o backend (plano free) → erro genérico na primeira mensagem

**Sintoma**: a primeira pergunta pro agente (proIAp) do dia dava um erro genérico ("Ops! Tivemos uma dificuldade temporária...", HTTP 500), e tentar de novo logo em seguida funcionava normalmente.

**Causa**: o plano gratuito do Render **desliga o serviço depois de um tempo sem uso**. Quando chega uma requisição depois disso, o Render religa o serviço, e essa primeira leva de tráfego costuma vir com alguma falha (conexão com banco/API de IA ainda "esfriada", timeout etc.) — daí o 500 na primeira tentativa e sucesso na segunda.

**Correção** (parcialmente feita, uma parte ainda pendente):
- **Feito**: o chat do agente agora tenta de novo sozinho, uma vez, quando a resposta parece ser esse tipo de falha (500/502/503/504 ou sem resposta), mostrando "Servidor estava inativo, reconectando..." em vez de já jogar o erro feio pro tester ver.
- **Feito**: endpoint novo `GET /api/health` no backend (público, não toca banco nem IA) — existe só pra servir de alvo de um "ping" externo.
- **Pendente**: configurar, em um serviço gratuito tipo [cron-job.org](https://cron-job.org) ou UptimeRobot, um ping em `https://bicentral-backend.onrender.com/api/health` a cada ~10 minutos. Isso mantém o Render "acordado" o tempo todo e faz esse problema quase nunca mais acontecer na prática — mas é uma conta/configuração fora do código, então só quem tem acesso ao Render consegue fazer.

## Problema 4 — Qualquer rota direta (`/admin`, `/login`, F5 em qualquer página) dava 404

### Conceito rápido: por que isso acontece numa SPA

O Angular gera, no fim das contas, **um único arquivo HTML real**: `index.html`. É ele que carrega o JavaScript da aplicação, e é esse JavaScript, já rodando no navegador, que decide "a URL é `/admin`, então mostro o componente de admin" — isso é o **Angular Router**, funcionando inteiramente no lado do cliente (SPA = *Single Page Application*, "uma página só").

Clicar em links **dentro** do app sempre funciona, porque o Angular já está rodando e só troca o que aparece na tela, sem pedir nada novo pro servidor.

O problema é a **primeira** carga de uma URL que não seja a raiz: digitar `/admin` direto, ou dar F5 nela. Nesse caso, quem responde primeiro não é o Angular — é o **servidor** (Render). Se o servidor for ingênuo sobre isso, ele procura um arquivo chamado `admin` na pasta do site, não acha, e devolve 404 puro, sem nunca chegar a entregar o `index.html` pro Angular decidir a rota.

A solução padrão é configurar um **fallback** (também chamado de *rewrite*, "reescrita"): "se o caminho pedido não bate com nenhum arquivo real (JS, CSS, imagem...), devolve o `index.html` mesmo assim, e deixa o Angular resolver no navegador." *Rewrite* é diferente de *redirect*: redirect trocaria a URL que aparece na barra de endereço; rewrite mantém `/admin` visível e só troca o conteúdo entregue por baixo.

### O que a gente tentou (e não era isso)

1. **Permissão de admin** (você não ser admin no banco de produção) — descartado porque **todas** as rotas davam 404, não só `/admin` (testamos `/login` também).
2. **Build/deploy desatualizado ou pasta errada** — descartado: o `Publish Directory` configurado no Render (`dist/BICentral-frontend/browser`) é exatamente onde o Angular gera os arquivos, e o log de build confirmou isso.
3. **Arquivo `_redirects` com erro de conteúdo/encoding** — descartado: comparamos byte a byte o arquivo do repositório com o que estava sendo servido ao vivo. Eram idênticos.

### A causa raiz

O terceiro ponto merece contexto: `_redirects` é um arquivo de configuração (sem extensão), em `frontend/public/_redirects`, com uma regra "qualquer caminho (`/*`) cai no `index.html`". Esse formato é da **Netlify** (outra plataforma de hospedagem) — vários outros hosts de site estático copiaram esse formato por ser simples e ter virado meio padrão de fato.

Testando a URL ao vivo (`https://bicentral-frontend.onrender.com/_redirects`), o arquivo voltava com `Content-Type: binary/octet-stream` — ou seja, o Render estava só **servindo o arquivo como um arquivo qualquer**, não lendo ele como configuração. Fomos direto na documentação oficial do Render: **Render Static Site não suporta o formato `_redirects` da Netlify.** Ele só reconhece regras de redirect/rewrite cadastradas **na tela do dashboard** (Settings → Redirects/Rewrites) — não por arquivo.

Ou seja: o arquivo `_redirects` nunca fez nada, desde o primeiro deploy nesse host. Só não tinha dado problema até agora porque os testes provavelmente sempre foram clicando **dentro** do app (o caso que sempre funciona — ver seção anterior), nunca abrindo um link direto ou dando F5 numa página interna.

### A correção

No dashboard do Render, serviço **bicentral-frontend** → Settings → **Redirects/Rewrites**, regra:

| Source | Destination | Action |
|---|---|---|
| `/*` | `/index.html` | **Rewrite** |

(O detalhe importante era trocar a Action de "Redirect", que é o padrão do formulário, pra "Rewrite".)

O arquivo `frontend/public/_redirects` foi mantido no repositório — não faz mal estar lá —, só ganhou um comentário no topo avisando que ele não tem efeito nesse host, pra ninguém perder tempo mexendo nele de novo achando que é ali que mora a configuração.

Confirmado com teste direto na URL ao vivo: `/login` e `/admin` passaram a devolver `200` com o `index.html` de verdade, em vez do 404 puro do Render.

---

## Resumo

| # | Problema | Onde a causa mora | Status |
|---|---|---|---|
| 1 | POST via proxy `_redirects` voltava vazio | Limitação do Render (proxy de Static Site) | ✅ Corrigido (chamadas diretas + CORS) |
| 2 | Preflight `OPTIONS` levava 401 | `SecurityConfig` exigindo login até no preflight | ✅ Corrigido |
| 3 | Render dorme o backend free tier → 500 | Limitação do plano gratuito do Render | ✅ Front tolera melhor / ⏳ falta configurar o ping externo |
| 4 | Rota direta / F5 dava 404 | Render Static Site não lê `_redirects` | ✅ Corrigido (regra Rewrite no dashboard) |
