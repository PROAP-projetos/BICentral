# Sprint 1 - Neci

## Minha parte

Criar a tela do chat do Agente de IA.

## Referencia visual

Usar como base a imagem de referencia enviada na conversa:

![Referencia da tela de ingestao para IA](docs/referencia-tela-ingestao-ia.png)

Essa imagem serve como direcao visual para organizacao da tela: area principal grande, painel lateral com metadados e resumo, botoes claros e estados de processamento.

Importante: remover o campo **Painel relacionado**. Esse campo nao sera usado agora.

Na tela de ingestao/chat do agente, manter apenas o que faz sentido para o agente:

- Upload ou area principal de interacao.
- Equipe/origem.
- Nivel de acesso do documento: `PUBLICO` ou `PRIVADO`.
- Resumo dos arquivos/documentos.
- Botao principal de confirmacao ou envio.

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

O que ainda falta para o agente:

- Tela de chat.
- Endpoint oficial do agente.
- Embeddings.
- Supabase Vector.
- Busca semantica.
- Resposta final usando Gemini com os chunks encontrados.

Importante:

- Documento publico: pode ser consultado por qualquer usuario autenticado.
- Documento privado: so pode ser consultado por usuarios/equipes autorizadas.
- Quem decide isso e o backend/IA na hora da busca. A tela nao deve deixar o usuario escolher se quer buscar em documento publico ou privado.
- Nesta sprint, o chat nao tera campo de publico/privado.
- O usuario apenas pergunta; o sistema aplica a permissao sozinho.

## O que eu preciso fazer agora

- Criar uma tela para o Agente IA.
- Colocar um campo para digitar a pergunta.
- Colocar um botao para enviar.
- Criar uma area para mostrar a resposta.
- Mostrar quando estiver carregando.
- Mostrar mensagem quando der erro.
- Deixar um espaco para mostrar as fontes usadas pela IA.
- Conectar a tela ao endpoint:

`POST /api/agente/perguntar`

## O que eu preciso entregar no fim da sprint

- Tela de chat funcionando.
- Usuario consegue enviar uma pergunta.
- Resposta aparece na tela.
- Tela mostra carregamento enquanto espera.
- Tela mostra erro se a chamada falhar.

## Posso comecar agora?

Sim. Pode comecar mesmo antes da IA estar pronta.

Enquanto a Dallyla termina a IA, Lean pode criar uma resposta falsa temporaria para voce testar a tela.

## Dependo de alguem?

Dependo do Lean criar o endpoint:

`POST /api/agente/perguntar`

No inicio, esse endpoint pode retornar uma resposta falsa so para testar o frontend.

## Exemplo do que a tela vai enviar

```json
{
  "pergunta": "Qual e a politica visual da UFT?",
  "equipe": "COMUNICACAO"
}
```

## Exemplo do que a tela vai receber

```json
{
  "resposta": "Texto gerado pelo agente.",
  "fontes": [
    {
      "nomeArquivo": "manual-uft.pdf",
      "grupoId": "uuid-do-documento",
      "equipe": "COMUNICACAO",
      "visibilidade": "PUBLICO"
    }
  ]
}
```

## Nao preciso mexer nisso agora

- Embeddings.
- Supabase Vector.
- Logica da IA.
- Campo para escolher documento publico/privado no chat.
- Autenticacao.
- Regras de equipe.
