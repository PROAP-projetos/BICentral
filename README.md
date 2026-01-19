# BICentral

Sistema para centralizar e gerenciar paineis (Power BI) por usuario, com autenticacao, criacao de paines e captura automatica de capas.

## O que ja esta pronto
- Cadastro e login com JWT (inclui verificacao de email).
- CRUD de paineis: criar, listar, editar, excluir.
- Validacao de link do Power BI no backend.
- Captura de capa do painel em background com status (pendente/processando/concluida/erro).
- Front com lista, modais de adicionar/editar/excluir e feedback de erros.

## O que falta para a primeira versao
- Equipes e papeis (admin/editor/viewer).
- Restricao de paineis por equipe e permissao.
- Upload de arquivos .ipynb e associacao com equipe/painel.
- Logs de auditoria e reset de senha.

## Principais tecnologias
- Backend: Spring Boot, JWT, Playwright (captura de capa), Supabase Storage.
- Frontend: Angular.

## Como rodar localmente (basico)
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
- backend/: API e servicos (autenticacao, paineis, captura de capa).
- frontend/: UI Angular (login, cadastro, home, modais de paineis).

## Observacoes
- O link do Power BI precisa comecar com `https://app.powerbi.com/view?r=`.
- A captura da capa roda de forma assincrona para nao travar o cadastro do painel.
