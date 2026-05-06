# Sprint 1 - Dallyla

## Minha parte

Fazer a base de IA da ingestao funcionar: embeddings, Supabase Vector e busca semantica inicial.

## O que eu preciso fazer agora

- Testar se o Gemini esta respondendo no projeto.
- Configurar o modelo que vai gerar embeddings.
- Pegar os chunks que ja existem no `IngestaoService`.
- Transformar esses chunks em embeddings.
- Salvar os embeddings no Supabase Vector.
- Fazer uma primeira busca por similaridade.
- Respeitar a regra de visibilidade dos documentos na busca.

## Passo A Passo Para Fechar Minha Sprint

### 1. Conferir o que ja existe

- Abrir o arquivo `IngestaoService.java`.
- Confirmar que ele ja faz:
  - extracao de texto;
  - limpeza;
  - chunking;
  - criacao de `ChunkDTO`.
- Abrir o arquivo `ChunkDTO.java`.
- Ver quais metadados ja existem hoje:
  - `conteudo`;
  - `equipe`;
  - `acesso`;
  - `grupoId`;
  - `nomeArquivo`.
- Abrir o arquivo `AiConfig.java`.
- Confirmar se o Gemini ja esta configurado.
- Testar o endpoint:

`GET /ai/test/gemini?msg=teste`

### 2. Decidir e configurar embeddings

- Escolher o modelo de embedding que sera usado.
- Ver se o LangChain4j ja tem dependencia para esse modelo.
- Se faltar dependencia, adicionar no backend.
- Criar configuracao do modelo de embedding no backend.
- Separar no codigo:
  - modelo de chat: Gemini;
  - modelo de embedding: usado para vetorizar texto.

### 3. Preparar Supabase Vector

- Confirmar se o banco Supabase esta acessivel pelo backend.
- Confirmar se a extensao vetorial esta disponivel no Supabase.
- Criar a estrutura/tabela para salvar os chunks vetorizados.
- A tabela precisa guardar pelo menos:
  - id do registro;
  - conteudo do chunk;
  - embedding/vetor;
  - nome do arquivo;
  - equipe;
  - visibilidade: `PUBLICO` ou `PRIVADO`;
  - grupo/documento de origem;
  - data de criacao.

### 4. Ligar chunk com embedding

- Pegar a lista de `ChunkDTO` gerada pelo `IngestaoService`.
- Para cada chunk:
  - pegar o campo `conteudo`;
  - gerar embedding;
  - montar metadados;
  - salvar no Supabase Vector.
- Garantir que o campo antigo `acesso` seja tratado como visibilidade:
  - `Publico`, `PUBLICO` ou equivalente vira `PUBLICO`;
  - `Privado`, `PRIVADO` ou equivalente vira `PRIVADO`.

### 5. Implementar busca semantica inicial

- Receber uma pergunta de teste.
- Gerar embedding da pergunta.
- Buscar no Supabase Vector os chunks mais parecidos.
- Retornar os chunks encontrados com:
  - conteudo;
  - nomeArquivo;
  - equipe;
  - visibilidade;
  - grupoId.

### 6. Aplicar regra de publico e privado

- Antes de retornar chunks para o Gemini, aplicar filtro:
  - documento `PUBLICO` pode entrar;
  - documento `PRIVADO` so entra se for da mesma equipe do usuario.
- Nunca retornar chunk privado de outra equipe.
- Testar manualmente:
  - pergunta buscando documento publico;
  - pergunta buscando documento privado da mesma equipe;
  - pergunta tentando recuperar documento privado de outra equipe.

### 7. Criar servico para o Lean chamar

- Criar ou preparar um servico de IA que o endpoint do Lean consiga chamar depois da ingestao.
- Esse servico deve receber chunks/metadados e salvar embeddings.
- Nome sugerido:

`AgenteIaService` ou `RagService`

- Metodo sugerido:

`indexarChunks(List<ChunkDTO> chunks)`

### 8. Fazer um teste de ponta a ponta da minha parte

- Rodar uma ingestao de teste usando arquivo PDF ou Excel.
- Confirmar que os chunks foram gerados.
- Confirmar que os embeddings foram salvos.
- Fazer uma pergunta de teste.
- Confirmar que a busca retorna chunks coerentes.
- Confirmar que a regra `PUBLICO`/`PRIVADO` esta sendo respeitada.

### 9. Separar evidencias para o TCC

- Tirar print ou anotar evidencias de:
  - Gemini respondendo;
  - chunks gerados;
  - embeddings salvos;
  - busca semantica funcionando;
  - filtro por equipe/visibilidade funcionando.
- Guardar exemplos de perguntas e respostas para avaliacao futura.

Regra de visibilidade que entra nesta sprint:

- Documento publico: pode ser usado para responder qualquer usuario autenticado.
- Documento privado: so pode ser usado se pertencer a equipe autorizada.
- A pergunta do usuario nao deve trazer `acesso`. A IA/backend deve filtrar isso automaticamente.
- Na busca semantica, o agente deve considerar:
  - chunks de documentos publicos;
  - chunks de documentos privados apenas da equipe do usuario.
- O agente nao pode usar chunk privado de outra equipe na resposta.

## O que eu preciso entregar no fim da sprint

- Gemini respondendo.
- Chunks transformados em embeddings.
- Vetores salvos no Supabase Vector.
- Busca semantica funcionando.
- Busca respeitando documentos publicos e privados conforme permissao da equipe.
- Servico de IA pronto para ser usado pelo endpoint de ingestao do Lean.

## Regra De Negocio Importante

Essa regra precisa estar funcionando de forma minima ja nesta sprint, porque os vetores ja precisam nascer com metadados corretos para impedir uso futuro de documento privado de uma equipe errada.

Na pratica:

- Se o documento for `PUBLICO`, ele pode entrar na busca.
- Se o documento for `PRIVADO`, ele so pode entrar na busca quando a equipe do documento for a mesma equipe do usuario.
- O filtro deve acontecer antes de retornar chunks para qualquer resposta futura do Gemini.
- Quando o chat for implementado, o prompt do Gemini so deve receber chunks que passaram por essa regra.

## Posso comecar agora?

Sim. Essa parte pode ser feita em paralelo com o frontend e o backend.

## Dependo de alguem?

Dependo do Lean apenas no final, para ligar meu servico de IA ao endpoint de ingestao:

`POST /api/ia/ingestao`

## Nao preciso mexer nisso agora

- Autenticacao.
- Regras de equipe.
- Tela do chat.
- Chunking, porque ja existe no codigo.
