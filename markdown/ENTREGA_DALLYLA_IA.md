# Entrega Dallyla - Sprint 1 IA

Este documento resume apenas o que ficou pronto na Sprint 1 da parte de IA e o que Lean e Neci podem usar agora.

## Minha entrega na Sprint 1

A base de IA para ingestao, embeddings, Supabase Vector e busca semantica inicial ficou pronta.

Foi entregue:

- Configuracao dos modelos de IA no backend.
- Modelo de chat Gemini para testes.
- Modelo de chat Ollama para testes.
- Modelo de embedding Gemini.
- Modelo de embedding Ollama como fallback.
- Geracao de embeddings a partir dos chunks.
- Salvamento dos chunks vetorizados no Supabase Vector.
- Metadados salvos junto com cada chunk.
- Funcoes SQL no Supabase para busca vetorial.
- Busca semantica inicial retornando chunks coerentes.
- Endpoint temporario para testar ingestao.
- Endpoint temporario para testar consulta semantica.

## Arquivos que podem ser usados

### Configuracao dos modelos

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/config/AiConfig.java`

Contem os beans:

- `geminiModel`
- `ollamaModel`
- `geminiEmbeddingModel`
- `ollamaEmbeddingModel`

### Servico de embeddings

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java`

Metodo principal:

```java
public void salvarChunks(List<ChunkDTO> chunks, Long equipeId)
```

Esse metodo:

- recebe uma lista de `ChunkDTO`;
- gera embedding Gemini;
- gera embedding Ollama;
- salva o chunk e os vetores no Supabase.

### Servico de ingestao

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java`

O fluxo de ingestao ja consegue chamar o servico de embeddings.

Fluxo disponivel:

```text
arquivo
-> extracao de texto
-> limpeza
-> chunking
-> metadados
-> embeddings
-> Supabase Vector
```

### Servico de consulta semantica

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/service/ConsultaService.java`

Metodo principal:

```java
public List<String> buscar(String pergunta, Long equipeId)
```

Esse metodo:

- recebe a pergunta do usuario;
- transforma a pergunta em embedding;
- chama a funcao SQL do Supabase;
- retorna os chunks mais similares.

## Estrutura salva no Supabase

Tabela usada:

`embeddings`

Campos usados:

- `id`
- `content`
- `source`
- `equipe_id`
- `visibilidade`
- `metadata`
- `embedding_gemini`
- `embedding_ollama`

Regra atual de visibilidade no salvamento:

```text
Privado -> PRIVADO
qualquer outro valor -> PUBLICO
```

## Funcoes SQL criadas no Supabase

Foram criadas:

- `buscar_por_gemini`
- `buscar_por_ollama`

Essas funcoes recebem:

- `query_embedding`
- `equipe_id_usuario`
- `match_count`

E retornam os chunks mais parecidos usando busca vetorial.

O retorno precisa manter o campo:

```text
content
```

porque o `ConsultaService` le esse campo para montar a lista de chunks.

## Endpoints temporarios disponiveis

Arquivo:

`backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java`

### Testar Gemini

```http
GET /ai/test/gemini?msg=teste
```

### Testar Ollama

```http
GET /ai/test/ollama?msg=teste
```

### Testar ingestao com embeddings

```http
POST /ai/test/ingestao
```

Parametros:

- `caminhoArquivo`
- `equipe`
- `acesso`
- `nomeArquivo`
- `equipeId`

Esse endpoint processa o documento, gera chunks, gera embeddings e salva no Supabase.

### Testar consulta semantica

```http
GET /ai/test/consulta?pergunta=...&equipeId=...
```

Exemplo:

```http
GET /ai/test/consulta?pergunta=para%20que%20serve%20o%20bicentral%20segundo%20o%20documento%20teste&equipeId=1
```

Esse endpoint retorna uma lista de chunks semanticamente similares.

## O que o Lean pode usar na Sprint 1

O Lean pode usar a parte pronta de IA para conectar o endpoint oficial de ingestao:

```http
POST /api/ia/ingestao
```

ao fluxo existente:

```text
IngestaoService
-> EmbeddingService
-> Supabase Vector
```

Servico disponivel para salvar embeddings:

```java
EmbeddingService.salvarChunks(List<ChunkDTO> chunks, Long equipeId)
```

O endpoint oficial do Lean ainda deve cuidar de:

- receber o arquivo enviado pela tela;
- receber equipe/origem;
- receber visibilidade `PUBLICO` ou `PRIVADO`;
- validar usuario/equipe conforme as regras do backend;
- chamar o fluxo de ingestao;
- devolver uma resposta padronizada para o frontend.

## O que a Neci pode usar na Sprint 1

A Neci pode seguir conectando a tela ao endpoint oficial do Lean:

```http
POST /api/ia/ingestao
```

A parte de IA ja espera receber, direta ou indiretamente:

- arquivo;
- equipe/origem;
- visibilidade do documento;
- `equipeId`.

Para a tela, os campos importantes continuam sendo:

- upload do arquivo;
- equipe/origem;
- nivel de acesso: `PUBLICO` ou `PRIVADO`;
- botao de confirmar ingestao;
- status de processamento.

A tela nao precisa conhecer embeddings, Supabase Vector ou funcoes SQL.

## Observacoes importantes

- Os endpoints `/ai/test/**` sao temporarios e servem para validacao tecnica.
- A regra de visibilidade precisa ser mantida: documento `PUBLICO` pode ser consultado futuramente por usuarios autenticados; documento `PRIVADO` deve ficar restrito a equipe correta.
- O campo `content` no retorno da busca semantica nao deve ser removido sem ajustar o `ConsultaService`.

