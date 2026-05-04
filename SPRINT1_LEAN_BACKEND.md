# Sprint 1 - Lean

## Minha parte

Criar o caminho entre a tela do chat e o Agente de IA.

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

- Endpoint oficial do agente.
- Ligacao entre endpoint e servico de IA.
- Embeddings.
- Supabase Vector.
- Busca semantica.
- Resposta final usando Gemini com os chunks encontrados.

Importante:

- Documento publico: pode ser consultado por qualquer usuario autenticado.
- Documento privado: so pode ser consultado por usuarios/equipes autorizadas.
- O usuario nao escolhe se a busca sera publica ou privada.
- O backend deve enviar para a IA o contexto do usuario/equipe, e a busca deve considerar:
  - documentos publicos;
  - documentos privados permitidos para a equipe do usuario.
- Essa regra minima entra nesta sprint, porque protege o agente de usar documento privado de equipe errada.

## O que eu preciso fazer agora

- Criar o endpoint:

`POST /api/agente/perguntar`

- Fazer esse endpoint receber uma pergunta.
- Fazer esse endpoint receber a equipe.
- No primeiro momento, retornar uma resposta falsa para o frontend testar.
- Depois, trocar a resposta falsa pela chamada real ao servico da Dallyla.
- Retornar a resposta sempre no mesmo formato.
- Garantir que o endpoint use a autenticacao e as regras de equipe que ja existem.
- Garantir que a busca do agente respeite documentos publicos e privados.
- Nao deixar o frontend decidir livremente qual equipe/acesso sera usado sem validar com o usuario autenticado.

## O que eu preciso entregar no fim da sprint

- Endpoint `/api/agente/perguntar` funcionando.
- Frontend conseguindo chamar esse endpoint.
- Resposta voltando no formato combinado.
- Endpoint ligado ao servico real de IA quando a Dallyla finalizar.
- Regra minima de visibilidade funcionando:
  - documento `PUBLICO` pode ser usado;
  - documento `PRIVADO` so pode ser usado se for da equipe do usuario.

## Regra De Negocio Importante

O frontend nao deve mandar um campo `acesso` para escolher se a pergunta vai consultar documento publico ou privado.

Quem aplica essa regra e o backend junto com o servico de IA.

Na pratica:

- O backend identifica o usuario autenticado.
- O backend identifica a equipe ativa ou valida a equipe enviada.
- A IA deve buscar apenas:
  - documentos `PUBLICO`;
  - documentos `PRIVADO` vinculados a equipe do usuario.
- Documento privado de outra equipe nao pode ser enviado para o Gemini.

## Posso comecar agora?

Sim. Pode criar o endpoint com resposta falsa primeiro.

Isso libera a Neci para fazer a tela sem esperar a IA ficar pronta.

## Dependo de alguem?

No inicio, nao.

No final, depende da Dallyla entregar o servico de IA para substituir a resposta falsa pela resposta real.

## Exemplo do que o endpoint vai receber

```json
{
  "pergunta": "Qual e a politica visual da UFT?",
  "equipe": "COMUNICACAO"
}
```

## Exemplo do que o endpoint vai devolver

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

- Tela do chat.
- Chunking, porque ja existe.
- Autenticacao do zero.
- Regras de equipe do zero.
