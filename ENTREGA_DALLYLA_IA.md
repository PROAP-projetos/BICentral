# Entrega Dallyla - IA, Embeddings e Busca Semantica

Este documento resume o que foi entregue na parte de IA e o que Lean e Neci podem usar nas proximas sprints.

## Status da minha entrega

A base de IA da Sprint 1 foi concluida.

Foi implementado e validado:

- Configuracao dos modelos de IA no backend.
- Geracao de embeddings com Gemini.
- Fallback de embeddings com Ollama.
- Transformacao dos chunks em vetores.
- Salvamento dos chunks vetorizados no Supabase Vector.
- Busca semantica inicial por similaridade.
- Funcoes SQL no Supabase para comparar vetores.
- Endpoint temporario de teste para consulta semantica.

Tambem foi adiantada uma parte da Sprint 2:

- Pergunta do usuario virando embedding.
- Embedding da pergunta sendo comparado com embeddings salvos.
- Retorno dos chunks semanticamente mais proximos.

Ainda nao foi implementado nesta entrega:

- Resposta final do agente usando Gemini.
- Prompt RAG definitivo.
- Tela de chat.
- Endpoint oficial `POST /api/agente/perguntar`.
- Citacao de fontes na resposta final.
- Auditoria de perguntas/respostas.

Esses itens pertencem as proximas sprints.

## Arquivos principais da minha parte

### Configuracao de IA

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/config/AiConfig.java`

Responsavel por configurar:

- `geminiModel`: modelo de chat Gemini.
- `ollamaModel`: modelo de chat Ollama.
- `geminiEmbeddingModel`: modelo de embedding Gemini.
- `ollamaEmbeddingModel`: modelo de embedding Ollama.

### Salvamento de embeddings

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java`

Metodo principal:

```java
public void salvarChunks(List<ChunkDTO> chunks, Long equipeId)
```

Esse metodo recebe os chunks ja gerados, cria embeddings e salva no Supabase.

Campos salvos na tabela `embeddings`:

- `id`
- `content`
- `source`
- `equipe_id`
- `visibilidade`
- `metadata`
- `embedding_gemini`
- `embedding_ollama`

Regra atual de visibilidade:

```java
chunk.getAcesso().equalsIgnoreCase("Privado") ? "PRIVADO" : "PUBLICO"
```

Ou seja:

- `Privado` vira `PRIVADO`.
- Qualquer outro valor vira `PUBLICO`.

### Integracao com ingestao existente

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java`

A ingestao ja chama a parte de embeddings pelo fluxo:

```text
arquivo
-> extracao de texto
-> limpeza
-> chunking
-> ChunkDTO
-> salvarNoBanco(...)
-> EmbeddingService.salvarChunks(...)
-> Supabase Vector
```

### Busca semantica

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/ConsultaService.java`

Metodo principal:

```java
public List<String> buscar(String pergunta, Long equipeId)
```

Fluxo:

```text
pergunta do usuario
-> embedding da pergunta com Gemini
-> chamada RPC buscar_por_gemini no Supabase
-> retorno dos chunks similares
```

Se o Gemini falhar, o service usa fallback com Ollama:

```text
pergunta do usuario
-> embedding da pergunta com Ollama
-> chamada RPC buscar_por_ollama no Supabase
-> retorno dos chunks similares
```

## Funcoes criadas no Supabase

Foram criadas no Supabase as funcoes:

- `buscar_por_gemini`
- `buscar_por_ollama`

Essas funcoes recebem:

```text
query_embedding
equipe_id_usuario
match_count
```

E retornam chunks ordenados por similaridade vetorial.

O retorno esperado pelo backend atualmente precisa conter uma coluna chamada:

```text
content
```

Porque o `ConsultaService` le os resultados com:

```java
resultados.findValuesAsText("content")
```

Regra esperada dentro das funcoes SQL:

```text
retornar documentos PUBLICO
ou documentos PRIVADO da mesma equipe do usuario
```

Ou seja:

```sql
e.visibilidade = 'PUBLICO'
or e.equipe_id = equipe_id_usuario
```

## Endpoints temporarios de teste

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java`

### Teste Gemini

```http
GET /ai/test/gemini?msg=teste
```

Usado para validar se o Gemini esta respondendo.

### Teste Ollama

```http
GET /ai/test/ollama?msg=teste
```

Usado para validar se o Ollama esta respondendo.

### Teste de ingestao com embeddings

```http
POST /ai/test/ingestao
```

Parametros esperados:

```text
caminhoArquivo
equipe
acesso
nomeArquivo
equipeId
```

Esse endpoint:

- extrai texto;
- limpa texto;
- gera chunks;
- salva embeddings no Supabase;
- retorna os chunks para conferencia.

### Teste de busca semantica

```http
GET /ai/test/consulta?pergunta=...&equipeId=...
```

Exemplo:

```http
GET /ai/test/consulta?pergunta=para%20que%20serve%20o%20bicentral%20segundo%20o%20documento%20teste&equipeId=1
```

Esse endpoint retorna uma lista de chunks similares:

```json
[
  "chunk relevante 1",
  "chunk relevante 2",
  "chunk relevante 3"
]
```

Importante: este endpoint e temporario para teste. O endpoint oficial da proxima sprint deve ser definido pelo Lean.

## O que o Lean pode usar

### Para a Sprint 1

O Lean pode ligar o endpoint oficial de ingestao:

```http
POST /api/ia/ingestao
```

ao fluxo ja existente:

```text
IngestaoService
-> salvarNoBanco(...)
-> EmbeddingService.salvarChunks(...)
```

O servico que salva embeddings ja existe:

```java
EmbeddingService.salvarChunks(List<ChunkDTO> chunks, Long equipeId)
```

O endpoint oficial do Lean deve cuidar de:

- receber arquivo da tela;
- identificar usuario autenticado;
- validar equipe;
- validar permissao;
- receber visibilidade `PUBLICO` ou `PRIVADO`;
- chamar o fluxo de ingestao;
- retornar resposta padronizada para o frontend.

### Para a Sprint 2

Quando for criado o endpoint oficial:

```http
POST /api/agente/perguntar
```

o Lean pode chamar:

```java
ConsultaService.buscar(pergunta, equipeId)
```

Esse metodo retorna os chunks mais similares para a pergunta.

Ainda sera necessario, na Sprint 2:

- montar o contexto com esses chunks;
- chamar o Gemini para gerar a resposta final;
- tratar pergunta vazia;
- tratar equipe invalida;
- usar equipe do usuario autenticado;
- padronizar erros.

## O que a Neci pode usar

### Para a tela de ingestao

A Neci deve continuar seguindo o contrato esperado pelo Lean:

```http
POST /api/ia/ingestao
```

Campos importantes para a tela:

- arquivo;
- equipe/origem;
- visibilidade do documento: `PUBLICO` ou `PRIVADO`.

A tela nao precisa conhecer embeddings, Supabase Vector ou busca semantica.

Ela so precisa enviar os dados corretos para o endpoint oficial de ingestao.

### Para a tela de chat futura

Na Sprint 2, a tela de chat deve chamar o endpoint oficial que o Lean criar:

```http
POST /api/agente/perguntar
```

A tela nao deve deixar o usuario escolher se quer buscar em documento publico ou privado.

Essa regra fica no backend:

- documento `PUBLICO` pode ser usado por qualquer usuario autenticado;
- documento `PRIVADO` so pode ser usado pela equipe autorizada;
- documento privado de outra equipe nunca deve ser enviado ao Gemini.

## Regras de negocio ja consideradas

A busca semantica deve respeitar:

- chunks de documentos publicos podem entrar na busca;
- chunks de documentos privados so entram para usuarios da mesma equipe;
- a pergunta do usuario nao recebe `PUBLICO` ou `PRIVADO`;
- o backend filtra automaticamente pelo `equipeId`;
- chunk privado de outra equipe nao deve ser retornado nem usado no prompt futuro.

## Pontos importantes para nao quebrar a integracao

- As funcoes do Supabase precisam continuar com os nomes:
  - `buscar_por_gemini`
  - `buscar_por_ollama`
- O retorno das funcoes precisa conter `content`, porque o backend le esse campo.
- A tabela `embeddings` precisa manter os campos:
  - `content`
  - `source`
  - `equipe_id`
  - `visibilidade`
  - `metadata`
  - `embedding_gemini`
  - `embedding_ollama`
- O `equipeId` usado na consulta deve representar a equipe autorizada do usuario.
- O endpoint `/ai/test/consulta` e apenas de teste, nao deve ser tratado como endpoint final do produto.

## Proximos passos sugeridos

### Lean

- Criar endpoint oficial `POST /api/ia/ingestao`.
- Conectar esse endpoint ao fluxo de ingestao e embeddings.
- Na Sprint 2, criar `POST /api/agente/perguntar`.
- Usar `ConsultaService.buscar(...)` como base para recuperar contexto.

### Neci

- Conectar a tela de ingestao ao `POST /api/ia/ingestao` quando estiver pronto.
- Na Sprint 2, criar a tela de chat.
- Preparar exibicao de resposta e, futuramente, fontes citadas.

### Dallyla

- Na Sprint 2, transformar os chunks recuperados em contexto.
- Criar o prompt RAG.
- Chamar Gemini para resposta final.
- Impedir resposta quando nao houver contexto suficiente.
- Retornar fontes na Sprint 3.

