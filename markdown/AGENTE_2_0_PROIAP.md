# Agente 2.0 - Evolucao Do proIAp

Este documento organiza a visao de evolucao do proIAp alem do agente RAG inicial.
A ideia e tratar o agente nao apenas como um chat com documentos, mas como uma
plataforma institucional de inteligencia aumentada integrada ao BICentral.

## Visao Geral

O proIAp 2.0 e uma evolucao do agente conversacional da PROAP/UFT para um sistema
capaz de:

- consultar documentos institucionais com RAG;
- manter historico e memoria conversacional;
- controlar uso, cotas e custos;
- operar com contingencia local via Ollama;
- gerar graficos, paineis e analises a partir dos dados;
- registrar auditoria e fontes utilizadas;
- apoiar a tomada de decisao institucional.

Frase-sintese:

> O proIAp nao se limita a um agente conversacional baseado em RAG; ele constitui a
> base para uma plataforma institucional de inteligencia aumentada, com memoria
> conversacional, governanca de uso, contingencia local, rastreabilidade, geracao de
> visualizacoes e apoio analitico a tomada de decisao.

## Camada 1 - Nucleo RAG Funcional

Esta e a base do agente atual.

Objetivo:

- permitir que o usuario faca perguntas em linguagem natural;
- recuperar documentos relevantes;
- montar contexto;
- gerar resposta fundamentada com IA.

Componentes:

- ingestao de PDF e XLSX;
- extracao e limpeza de texto;
- fatiamento em chunks;
- geracao de embeddings;
- armazenamento no Supabase com pgvector;
- busca semantica;
- filtro por equipe e visibilidade;
- chamada ao Gemini;
- resposta final no chat.

Status esperado:

- usuario envia documentos;
- documentos sao vetorizados;
- usuario pergunta;
- agente responde usando contexto recuperado;
- documentos privados de outra equipe nao entram na resposta.

## Camada 2 - Memoria E Multiplos Chats

Nesta etapa, o agente deixa de responder apenas perguntas isoladas e passa a lidar com
continuidade conversacional.

Funcionalidades:

- multiplos chats por usuario;
- multiplos chats por equipe;
- titulo automatico da conversa;
- historico persistente;
- retomada de conversas anteriores;
- memoria curta dentro da conversa atual;
- memoria persistente controlada;
- exclusao de conversas;
- limpeza de memoria por solicitacao do usuario;
- politicas de retencao alinhadas a LGPD.

Possiveis entidades:

- `chat_sessions`;
- `chat_messages`;
- `agent_memory`;

Campos importantes:

- usuario;
- equipe;
- titulo;
- data de criacao;
- ultima atualizacao;
- pergunta;
- resposta;
- fontes usadas;
- modelo utilizado;
- quantidade estimada de tokens.

Cuidados:

- memoria nao deve vazar dados entre equipes;
- memoria nao deve guardar informacao sensivel sem necessidade;
- usuario deve conseguir apagar historico;
- administradores devem ter regras claras de acesso aos logs.

## Camada 3 - Governanca, Quotas E Auditoria

Esta camada transforma o agente em um recurso institucional controlado.

Funcionalidades:

- limite de perguntas por usuario;
- limite de perguntas por equipe;
- quota diaria, mensal ou por periodo;
- registro de consumo;
- estimativa de custo por modelo;
- bloqueio quando limite for atingido;
- avisos antes de atingir o limite;
- painel administrativo de uso;
- logs de auditoria.

Dados de auditoria:

- usuario;
- equipe;
- pergunta;
- data e hora;
- modelo usado;
- fontes recuperadas;
- visibilidade das fontes;
- tempo de resposta;
- status da chamada;
- erro, quando houver.

Beneficios:

- evita uso descontrolado da API;
- melhora previsibilidade de custo;
- permite rastrear decisoes apoiadas pela IA;
- fortalece a seguranca institucional.

## Camada 4 - Contingencia Com Ollama

O Gemini permanece como modelo principal, mas o Ollama passa a atuar como contingencia
local e opcao de resiliencia.

Objetivo:

- manter o agente funcionando mesmo quando a API externa falhar;
- reduzir dependencia de servicos pagos;
- permitir testes locais;
- comparar qualidade entre modelos.

Fluxo proposto:

1. Tentar Gemini.
2. Se Gemini falhar, tentar Ollama.
3. Registrar qual modelo foi usado.
4. Informar modo degradado quando necessario.

Aplicacoes:

- embedding via `gemini-embedding-001`;
- fallback de embedding via `nomic-embed-text`;
- resposta via `gemini-2.5-flash`;
- fallback de resposta via modelo local Ollama.

Cuidados:

- embeddings da consulta devem usar a mesma familia dos embeddings salvos;
- busca Gemini deve consultar coluna Gemini;
- busca Ollama deve consultar coluna Ollama;
- respostas do modo local podem ter qualidade diferente;
- logs devem registrar fallback.

## Camada 5 - Fontes Citadas E Transparencia

Esta etapa torna as respostas auditaveis e confiaveis.

Funcionalidades:

- mostrar fontes usadas em cada resposta;
- exibir nome do arquivo;
- exibir tipo de fonte;
- exibir visibilidade;
- exibir equipe;
- exibir trecho recuperado;
- exibir score de similaridade, se disponivel;
- permitir abrir ou baixar o documento original, quando autorizado.

Resultado esperado:

- cada resposta deve indicar de onde veio;
- resposta sem fonte deve ser tratada como nao confiavel;
- o agente deve evitar responder quando nao houver contexto suficiente.

## Camada 6 - Graficos, Paineis E Analise De Dados

Esta e a evolucao do agente para apoio analitico.

Objetivo:

- ir alem de respostas textuais;
- gerar visualizacoes a partir de planilhas e dados estruturados;
- apoiar analise de indicadores e metas.

Funcionalidades:

- gerar graficos a partir de XLSX;
- identificar colunas numericas, categorias e datas;
- sugerir visualizacoes adequadas;
- criar resumos de indicadores;
- comparar metas e resultados;
- detectar atrasos, desvios e tendencias;
- criar paineis simples dentro do BICentral;
- exportar visualizacoes para relatorio.

Exemplos de perguntas:

- "Gere um grafico das tarefas por status."
- "Quais metas estao atrasadas?"
- "Compare os indicadores do TCU por ano."
- "Monte um painel resumido do PAT."
- "Quais acoes tem maior risco de atraso?"

Possiveis saidas:

- texto explicativo;
- tabela resumida;
- grafico de barras;
- grafico de linha;
- grafico de pizza, quando fizer sentido;
- cards de indicadores;
- painel automatico.

Cuidados:

- graficos devem indicar a fonte dos dados;
- dados privados continuam respeitando equipe e permissao;
- o agente deve explicar quando nao conseguir inferir colunas corretamente;
- visualizacoes devem ser revisaveis pelo usuario.

## Camada 7 - Avaliacao Para TCC

Esta camada conecta a evolucao tecnica ao valor academico do projeto.

Metricas:

- tempo medio de resposta do agente;
- tempo medio de busca manual;
- acuracia informacional;
- completude da resposta;
- satisfacao do usuario;
- quantidade de fontes consultadas;
- taxa de respostas sem contexto suficiente;
- comparacao Gemini versus Ollama;
- custo estimado por consulta.

Protocolo possivel:

1. Selecionar perguntas reais da rotina da PROAP.
2. Pedir que servidores respondam manualmente.
3. Fazer as mesmas perguntas ao proIAp.
4. Comparar tempo, acuracia e completude.
5. Registrar percepcao dos usuarios.
6. Analisar ganhos e limitacoes.

Evidencias para o TCC:

- prints da ingestao;
- prints do chat;
- exemplos de fontes citadas;
- tabela de comparacao manual versus agente;
- graficos de desempenho;
- logs anonimizados;
- arquitetura do sistema.

## Priorizacao Recomendada

### Curto Prazo

- estabilizar RAG atual;
- retornar fontes usadas por resposta;
- exibir fontes citadas no frontend;
- melhorar tratamento de erro;
- garantir filtro publico/privado no Supabase;
- registrar logs basicos de pergunta e resposta.

### Medio Prazo

- historico de chats;
- multiplas conversas por usuario;
- auditoria completa;
- quota por usuario/equipe;
- fallback de resposta com Ollama;
- painel administrativo de uso.

### Longo Prazo

- memoria persistente;
- geracao de graficos;
- criacao automatica de paineis;
- avaliacao comparativa completa;
- relatorios automaticos para gestao;
- recomendacoes proativas do agente.

## Riscos

- vazamento de informacao entre equipes;
- respostas sem fonte confiavel;
- custo excessivo com API externa;
- baixa qualidade de resposta com documentos mal estruturados;
- dificuldade de avaliar acuracia;
- excesso de escopo para o TCC;
- dependencia de funcoes RPC no Supabase sem versionamento claro.

## Decisoes Pendentes

- manter rota `POST /api/ia/consulta` ou criar `POST /api/agente/perguntar`;
- definir modelo oficial de memoria;
- definir politica de retencao de chats;
- definir limite inicial de quota;
- definir quando acionar Ollama;
- definir formato padrao de fontes citadas;
- definir quais graficos serao suportados primeiro;
- definir protocolo de avaliacao com servidores da PROAP.

## Conclusao

O Agente 2.0 posiciona o proIAp como uma camada inteligente do BICentral. O projeto
comeca com RAG sobre documentos institucionais, mas pode evoluir para uma plataforma
com memoria, governanca, auditoria, contingencia local e visualizacao analitica.

Para o TCC, o caminho mais seguro e entregar primeiro o nucleo RAG com fontes e
avaliacao. As demais camadas devem aparecer como evolucao planejada, mostrando que o
projeto tem continuidade tecnica e valor institucional alem da primeira versao.
