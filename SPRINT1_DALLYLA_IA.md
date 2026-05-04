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
