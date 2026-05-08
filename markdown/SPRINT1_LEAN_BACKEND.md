# Sprint 1 - Lean

## Minha parte

Criar o caminho entre a tela de ingestao da Neci e o pipeline de IA da Dallyla.

## Em que pe esta o agente hoje

O projeto ja tem a parte de ingestao implementada no backend.

Ja existe:

- Extracao de texto de PDF.
- Extracao de texto de Excel `.xlsx`.
- Limpeza do texto.
- Chunking/fatiamento do texto.
- Criacao de chunks com metadados.
- Endpoint de teste para gerar preview da ingestao em JSON.

Onde esta no codigo:

- `backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java`
- `backend/src/main/java/com/bicentral/bicentral_backend/dto/ChunkDTO.java`
- `backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java`

O que ainda falta para esta sprint:

- Endpoint oficial de ingestao.
- Receber arquivo pela tela da Neci.
- Receber equipe/origem.
- Receber visibilidade do documento: `PUBLICO` ou `PRIVADO`.
- Chamar o `IngestaoService`.
- Ligar a ingestao com os embeddings/Supabase Vector da Dallyla.

## Regra De Negocio Importante

Na ingestao, o usuario escolhe se o documento sera `PUBLICO` ou `PRIVADO`.

Essa escolha fica salva como metadado do documento/chunk.

Na pratica:

- Documento `PUBLICO`: pode ser consultado futuramente por qualquer usuario autenticado.
- Documento `PRIVADO`: so pode ser consultado futuramente pela equipe autorizada.
- Documento privado precisa ficar vinculado a equipe correta no momento da ingestao.
- O backend nao deve aceitar equipe/visibilidade sem validar com o usuario autenticado e as regras ja existentes.

## O que eu preciso fazer agora

- Criar o endpoint:

`POST /api/ia/ingestao`

- Fazer esse endpoint receber arquivo.
- Fazer esse endpoint receber equipe/origem.
- Fazer esse endpoint receber visibilidade: `PUBLICO` ou `PRIVADO`.
- Chamar o `IngestaoService` ja existente para extrair, limpar e fatiar o texto.
- Retornar uma resposta simples para a tela da Neci testar.
- Depois, integrar com o servico da Dallyla para gerar embeddings e salvar no Supabase Vector.
- Garantir que documento privado fique vinculado a equipe correta.

## O que eu preciso entregar no fim da sprint

- Endpoint `/api/ia/ingestao` funcionando.
- Frontend da Neci conseguindo enviar documento.
- Backend usando o `IngestaoService` ja existente.
- Documento/chunks gerados com equipe e visibilidade.
- Endpoint pronto para chamar a geracao de embeddings da Dallyla.

## Posso comecar agora?

Sim. Pode criar o endpoint com retorno simples primeiro.

Isso libera a Neci para fazer a tela sem esperar Supabase Vector ficar pronto.

## Dependo de alguem?

No inicio, nao.

No final, depende da Dallyla entregar o servico de embeddings/Supabase Vector para completar a ingestao real.

## Exemplo do que o endpoint vai receber

```text
arquivo: manual-uft.pdf
equipe: COMUNICACAO
visibilidade: PUBLICO
```

## Exemplo do que o endpoint vai devolver

```json
{
  "mensagem": "Documento enviado para ingestao.",
  "status": "PROCESSANDO"
}
```

## Nao preciso mexer nisso agora

- Tela de chat.
- Endpoint de pergunta do agente.
- Autenticacao do zero.
- Regras de equipe do zero.
- Chunking do zero, porque ja existe.
