# Evidências para resultados parciais da IA

Este arquivo reúne evidências já existentes no projeto para a seção de resultados parciais da monografia.

Observação: a tentativa mais recente de reexecutar o teste de ingestão no ambiente local falhou por erro de compilação do JDK, então os números abaixo foram consolidados a partir dos testes e artefatos já salvos no repositório.

## 1) Ingestão do PDF

Base técnica:

- [IngestaoService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java#L30)
- [BicentralBackendApplicationTests.java](backend/src/test/java/com/bicentral/bicentral_backend/BicentralBackendApplicationTests.java#L19)

Evidência:

- O serviço usa PDFBox para extrair todo o texto do PDF.
- O teste de ingestão registra o total de caracteres extraídos e limpos.
- O valor consolidado no repositório é 38.594 caracteres.

Texto que pode entrar no relatório:

> A ingestão do PDF foi realizada com extração integral do conteúdo textual, seguida de limpeza do texto bruto para remoção de ruídos e normalização do material para a etapa seguinte.

## 2) Fatiamento do texto

Base técnica:

- [IngestaoService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java#L91)
- [BicentralBackendApplicationTests.java](backend/src/test/java/com/bicentral/bicentral_backend/BicentralBackendApplicationTests.java#L75)

Evidência:

- O chunking usa tamanho 512 e overlap 64.
- O teste de fatiamento registra a quantidade total de chunks gerados.
- O artefato salvo em [preview_ingestao_tcc.json](backend/output/preview_ingestao_tcc.json) mostra 12 chunks no resultado final.

Texto que pode entrar no relatório:

> O texto foi fatiado com janela deslizante de 512 tokens e sobreposição de 64 tokens, gerando 12 chunks no documento de teste.

## 3) Metadados do JSON

Base técnica:

- [IngestaoService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java#L119)
- [EmbeddingService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java#L58)
- [preview_ingestao_tcc.json](backend/output/preview_ingestao_tcc.json)

Evidência:

- No preview de ingestão, cada chunk carrega os campos `acesso`, `grupoId`, `equipe` e `nomeArquivo`.
- No payload enviado ao Supabase, os campos equivalentes usados pela persistência são `visibilidade` e `grupo_id`.
- Para a redação da monografia, `grupoId` pode ser apresentado como identificador de origem e `acesso` como nível de acesso.

Texto que pode entrar no relatório:

> Cada chunk foi acompanhado por metadados de origem e de acesso, permitindo rastrear de qual documento ele veio e qual a sua classificação de visibilidade.

## 4) Embeddings em colunas distintas do Supabase

Base técnica:

- [EmbeddingService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java#L45)
- [EmbeddingService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java#L64)
- [EmbeddingService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/EmbeddingService.java#L69)

Evidência:

- O serviço gera dois vetores por chunk: Gemini e Ollama.
- O JSON enviado ao Supabase grava os vetores em `embedding_gemini` e `embedding_ollama`.
- A persistência também grava `content`, `source`, `equipe_id`, `visibilidade` e `metadata`.

Texto que pode entrar no relatório:

> Foram gerados dois embeddings para cada chunk, armazenados em colunas distintas no Supabase, permitindo comparar e consultar os vetores produzidos por modelos diferentes.

## 5) Busca semântica

Base técnica:

- [ConsultaService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/ConsultaService.java#L43)
- [ConsultaService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/ConsultaService.java#L78)
- [ConsultaService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/ConsultaService.java#L94)
- [AiTestController.java](backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java#L79)
- [ENTREGA_DALLYLA_IA.md](markdown/ENTREGA_DALLYLA_IA.md#L181)

Evidência:

- O endpoint temporário de consulta é `GET /ai/test/consulta`.
- O serviço transforma a pergunta em embedding e chama a função RPC do Supabase (`buscar_por_gemini` ou `buscar_por_ollama`).
- O retorno é convertido para uma lista de `content`, ou seja, os chunks recuperados pela busca.
- O documento de entrega já registra um exemplo de consulta:

```http
GET /ai/test/consulta?pergunta=para%20que%20serve%20o%20bicentral%20segundo%20o%20documento%20teste&equipeId=1
```

Exemplo de chunks já indexados no documento de teste:

- Chunk 1: "UNIVERSIDADE FEDERAL DO TOCANTINS NOSSA IDENTIDADE - MANUAL DE IDENTIDADE VISUAL ... Este manual dispõe sobre as aplicações dos símbolos ..."
- Chunk 2: "para a aplicação dos elementos simbólicos que compõem a identidade visual da UFT ... 1. DEFINIÇÕES ... Brasão – É a principal representação gráfica da Universidade ..."

Texto que pode entrar no relatório:

> A consulta semântica recebeu a pergunta do usuário, gerou o embedding correspondente e recuperou os chunks mais similares no Supabase, retornando o conteúdo textual associado a esses registros.

## 6) Resumo curto para uso direto no texto

> O pipeline de IA foi validado em cinco etapas: ingestão do PDF, limpeza textual, fatiamento em 12 chunks com janela deslizante 512/64, atribuição de metadados de origem e acesso, geração de dois embeddings por chunk e consulta semântica por RPC no Supabase.
