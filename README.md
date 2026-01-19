# BICentral

Sistema para centralizar e gerenciar painéis (Power BI) por usuario, com autenticação, criação de painéis e captura automática de capas.

## O que ja está pronto
- Cadastro e login com JWT (inclui verificacao de email).
- CRUD de paineis: criar, listar, editar, excluir.
- Validação de link do Power BI no backend.
- Captura de capa do painel em background com status (pendente/processando/concluida/erro).
- Front com lista, modais de adicionar/editar/excluir e feedback de erros.

## O que falta para a primeira versão
- Equipes e papéis (admin/editor/viewer).
- Restricao de paineis por equipe e permissão.
- Upload de arquivos .ipynb e associação com equipe/painél.
- Logs de auditoria e reset de senha.

## Principais tecnologias
- Backend: Spring Boot, JWT, Playwright (captura de capa), Supabase Storage.
- Frontend: Angular.

## Como rodar localmente (básico)
Backend:
```
cd backend
./mvnw spring-boot:run
```

Frontend:
```
cd frontend
npm install
npm start
```

## Estrutura
- backend/: API e servicos (autenticação, painéis, captura de capa).
- frontend/: UI Angular (login, cadastro, home, modais de paineis).

## Observacoes
- O link do Power BI precisa comecar com `https://app.powerbi.com/view?r=`.
- A captura da capa roda de forma assíncrona para não travar o cadastro do painel.
