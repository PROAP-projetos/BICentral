# Sprint Tracker — BICentral

Quadro de sprints do estágio, com status por pessoa, bloqueio por dependência e aviso de atraso — sincronizado de verdade entre Dallyla, Neci e Lean via Supabase.

## Passo 1 — Banco de dados

No SQL Editor do Supabase (mesmo projeto do BICentral), rode o conteúdo inteiro de `supabase/schema.sql`. Isso cria as tabelas, ativa RLS e já popula as 25 tarefas do roteiro.

Se a data real de início da Sprint 1 for diferente de **07/08/2026**, ajuste as datas em `sprint_tracker_sprints` antes de rodar (ou depois, com um `update`).

## Passo 2 — Rodar local

```bash
cd sprint-tracker
npm install
cp .env.local.example .env.local
```

Edite `.env.local` e cole a `NEXT_PUBLIC_SUPABASE_ANON_KEY` (é a mesma chave `supabase.key` que já está em `backend/src/main/resources/application.properties` — **não** é a service-role-key).

```bash
npm run dev
```

Abra `http://localhost:3000`.

## Passo 3 — Deploy no Vercel

1. Crie um projeto novo no Vercel, importando o repositório do GitHub.
2. Em "Root Directory", aponte para `sprint-tracker`.
3. Em "Environment Variables", adicione `NEXT_PUBLIC_SUPABASE_URL` e `NEXT_PUBLIC_SUPABASE_ANON_KEY` com os mesmos valores do `.env.local`.
4. Deploy.

O link gerado é o que vocês três usam — sem login, qualquer um com o link vê e atualiza o status.
