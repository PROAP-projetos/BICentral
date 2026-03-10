# BICentral — Varredura Técnica e Caminho para Arquitetura RAG

## 1) Diagnóstico da stack atual

### Backend (Spring Boot)
**Estado atual**
- O backend já está em `Spring Boot 3.5.6` com `Java 17`, `spring-web`, `spring-security` e `spring-data-jpa`, base adequada para introduzir LangChain4j e pipeline de ingestão. 
- A organização em camadas (`controller`, `service`, `repository`, `model`) está clara e facilita extensão incremental.
- Hoje não há dependências de migração de banco (Flyway/Liquibase), o que aumenta risco de mudança estrutural sem versionamento.
- A lógica de painel ainda é centrada no usuário dono (`findBy...Usuario_Id`), sem consulta semântica, sem embeddings e sem vetor.

**Conclusão prática**
- O backend está **modular o suficiente para evoluir sem quebra grande**, desde que a evolução RAG entre em um módulo próprio (ex.: pacote `rag`) e com migrations desde o primeiro dia.

### Frontend (Angular)
**Estado atual**
- O shell global está em `app.html` com `<router-outlet>`, e a experiência principal está concentrada na `HomeComponent` (dashboard grid, modais, polling).
- `HomeComponent` já centraliza contexto de sessão e interação com dados, sendo o melhor ponto para acoplar o chat sem quebrar o fluxo atual.

**Ponto estratégico para Chat Integrado**
- Criar `ChatRagComponent` standalone e acoplar na `home.html` como painel lateral fixo (desktop) + gaveta (mobile).
- Se quiser evolução limpa, criar um `WorkspaceLayoutComponent` e mover dashboard + chat para lá, mantendo `HomeComponent` como orquestrador de estado.

---

## 2) Preparação do PGVector

### Banco atual
- O projeto já usa driver PostgreSQL no backend.
- Não há evidência no repositório de migration versionada para ativar `vector` (`CREATE EXTENSION vector`).

### Mudanças recomendadas
1. **Habilitar migrations** (Flyway) e criar `V1__...`/`V2__...` com:
   - `CREATE EXTENSION IF NOT EXISTS vector;`
   - tabela de chunks (ex.: `kb_chunk`) com:
     - `id`
     - `origem_id` (id do upload/documento)
     - `texto`
     - `embedding vector(768|1024|1536)` (depende do modelo)
     - `nivel_acesso` (enum/string)
     - `grupo_id` (FK lógica para equipe/grupo)
     - `metadata jsonb`
     - timestamps
2. **Indexação vetorial**:
   - `ivfflat` ou `hnsw` no campo embedding.
3. **JPA / acesso a vetor**:
   - em vez de depender de mapeamento JPA puro para `vector`, usar repositório com SQL nativo para `cosine_distance`/`<=>`.

---

## 3) Governança e Segurança

### Como está hoje
- Segurança stateless com JWT, filtro customizado (`JwtAuthenticationFilter`) e `SecurityContextHolder`.
- Endpoints públicos limitados (`/api/usuarios/cadastro`, `/login`, `/verify`), restante autenticado.
- O usuário autenticado entra como principal `Usuario`; porém não há authorities efetivas no `UserDetails` e o token atual não carrega claims de grupo/perfil.

### Como evoluir para Metadata Filtering
1. **Capturar contexto de autorização** no login:
   - incluir em claim JWT: `userId`, `grupos[]`, `rolesPorGrupo[]`, `nivelAcesso`.
2. **Criar serviço de contexto** (`AuthorizationContextService`) que lê `SecurityContext` e expõe:
   - grupos permitidos
   - nível de acesso máximo
3. **Aplicar filtro no momento da busca vetorial**:
   - consulta vetorial SEMPRE com cláusula adicional:
     - `grupo_id IN (:gruposPermitidos)`
     - `nivel_acesso <= :nivelUsuario`
4. **Blindagem final**:
   - nunca retornar chunk sem filtro, mesmo em fallback.

---

## 4) Pipeline de Ingestão (Uploads)

### Onde criar no backend
- Novo pacote `controller`/`service` dedicado ao RAG:
  - `controller/KnowledgeIngestionController`
  - `service/DocumentIngestionService`
  - `service/SpreadsheetParserService` (Apache POI/OpenCSV)
  - `service/ContextualShardingService`
  - `service/EmbeddingService` (LangChain4j + Gemini embedding model)
  - `service/VectorStoreService` (PGVector)

### Fluxo sugerido
1. Gestor sobe arquivo (`multipart/form-data`).
2. Validar permissão por grupo/equipe.
3. Extrair texto estruturado (planilha/csv).
4. Aplicar contextual sharding (chunk + contexto de aba/coluna/linha).
5. Gerar embedding por chunk.
6. Persistir chunk + embedding + metadados de segurança.
7. Registrar auditoria da ingestão.

---

## 5) Plano de ação imediato (3 tarefas de código para hoje)

### Tarefa 1 — Fundação de banco vetorial
- Adicionar Flyway no backend.
- Criar migration inicial com `CREATE EXTENSION vector` + tabela `kb_chunk` + índices + colunas de segurança (`origem_id`, `nivel_acesso`, `grupo_id`).

### Tarefa 2 — Esqueleto RAG no backend
- Adicionar dependências LangChain4j e criar pacote `rag` com:
  - `RagQueryService`
  - `PgVectorSearchRepository` (SQL nativo com metadata filtering)
  - DTOs de consulta/resposta de chat.
- Criar endpoint protegido `POST /api/rag/chat`.

### Tarefa 3 — Ingestão mínima funcional
- Criar endpoint `POST /api/rag/uploads` (Excel/CSV).
- Implementar parsing inicial (POI/OpenCSV) + chunking simples + persistência de embeddings e metadados.
- Deixar o chat já consultando apenas chunks autorizados.

---

## Sequência recomendada (curta)
1. Segurança e metadados primeiro (sem isso, RAG vaza dado).
2. Ingestão mínima em seguida (sem base, chat não responde).
3. UX de chat na Home por último (quando backend estiver confiável).
