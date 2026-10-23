# O que o proIAp sabe fazer hoje

Levantamento de tudo que o agente de IA do BICentral consegue responder e fazer, direto do código-fonte (não é uma lista de intenção — é o que está implementado e rodando). Última atualização: 14/09/2026.

## A ideia geral

O proIAp não é um chatbot genérico "conversando sobre PDFs". Ele é um **analista de dados com acesso direto ao banco institucional** da PROAP — quando alguém pergunta "qual departamento está pior no PAT", ele não adivinha nem resume um documento: ele roda uma consulta SQL de verdade no banco, pega o número exato, e responde com ele.

Isso é feito com **19 ferramentas** (`@Tool`, no jargão do LangChain4j) espalhadas em 3 categorias, mais um sistema de busca semântica (RAG) sobre documentos institucionais, mais um gerador de gráficos, mais um gerador de relatórios em DOCX.

## Duas fontes de conhecimento que se complementam

1. **Consulta estruturada ao banco** (as 19 ferramentas abaixo) — pra números exatos, rankings, tarefas pessoais, relatórios.
2. **Busca semântica (RAG)** sobre documentos institucionais que a equipe já subiu (PDFs, planilhas, normativas) — pra perguntas de "o que diz a norma X" ou contexto que não é um número de banco de dados.

O agente sabe explicar essa diferença sozinho se perguntarem "o que você faz" — está no próprio prompt dele.

## As 19 ferramentas

### PDI (Plano de Desenvolvimento Institucional — acumulado 2026-2030)

| Ferramenta | O que faz |
|---|---|
| `buscarPorCodigo` | Busca um item do PDI (Eixo, Objetivo, Ação) pelo código exato, ex: `1.1.1.3` |
| `buscarFilhosPorCodigoPai` | Lista os itens filhos diretos de um código pai (ex: `1.1.1` → todas as ações `1.1.1.x`) |
| `buscarPorAnoFinal` | Ações do PDI cuja data final é um ano específico |
| `contarPorMarcador` | Conta ações do PDI por marcador (ex: "CPA", "AUDIN"), com filtro opcional de % mínimo |
| `buscarPorTitulo` | Busca ações do PDI por palavra-chave no título |
| `contarAcoesPorDepartamentoPDI` | Ranqueia departamentos pela quantidade de ações do PDI sob sua responsabilidade |

### PAT (Plano Anual de Trabalho — ano corrente, por unidade)

| Ferramenta | O que faz |
|---|---|
| `ranquearDepartamentosPorExecucaoPAT` | Ranking de departamentos por execução média do PAT — melhores ou piores, com filtro UA/UG |
| `buscarExecucaoPATPorDepartamento` | As ações com menor execução de um departamento específico (até 15) |
| `buscarDetalhamentoDesempenhoDepartamento` | Relatório/panorama completo de uma unidade: contagem por faixa (zeradas/andamento/concluídas), média, extremos — instruído a **não tirar conclusão de "atraso" sozinho** a partir do número bruto |
| `contarAcoesPorDepartamentoPAT` | Ranqueia departamentos pela quantidade de ações do PAT (conta atribuições — uma ação compartilhada conta mais de uma vez) |
| `contarAcoesUnicasPAT` | Total de ações **únicas** do PAT (cada ação contada uma vez só, mesmo se compartilhada entre departamentos), com breakdown por UA/UG |
| `compararExecucaoPDIxPAT` | Compara a execução de uma mesma ação entre o acumulado (PDI) e o ano corrente (PAT) |
| `rastrearGargaloEmAcaoCompartilhada` | Pra ações divididas entre vários departamentos, aponta qual unidade está significativamente mais atrasada que as outras na mesma ação |

### Tarefas (nível mais granular — atividade individual, com responsável e prazo)

| Ferramenta | O que faz |
|---|---|
| `buscarMinhasTarefas` | Tarefas do próprio usuário logado — identifica sozinho quem está perguntando, sem precisar pedir o nome |
| `buscarTarefasPorDepartamento` | Até 10 tarefas mais críticas (menor % de conclusão) de um departamento, com ação, responsável e prazo |
| `buscarTarefasAtrasadasPorDepartamento` | Todas as tarefas com prazo vencido e não concluídas de um departamento — mesma lógica do painel de atrasos das notificações, agora disponível via chat |
| `buscarTarefaPorPalavraChave` | Busca tarefa por palavra-chave no título, em qualquer departamento ou só num específico |

Regra explícita no prompt: tarefas de uma **pessoa específica que não seja quem está perguntando** não são respondidas (só o próprio usuário ou um departamento inteiro) — proteção de escopo.

### Relatórios

| Ferramenta | O que faz |
|---|---|
| `solicitarGeracaoRelatorio` | Pede a geração de um relatório de desempenho completo em DOCX (PAT, PDI ou comparativo) — assíncrono, fica pronto em ~20-30s e aparece no ícone de documentos |
| `buscarUltimoRelatorioGerado` | Recupera o texto do último relatório que o usuário gerou, pra ele poder perguntar/aprofundar sobre o conteúdo sem reabrir o arquivo |

## Geração de gráficos (painéis)

Quando a pergunta pede visualização em vez de texto, um segundo "cérebro" (`gerarPainel`) monta um painel ECharts a partir dos dados já recuperados pelas ferramentas acima — nunca inventa número, só visualiza o que já veio do banco. Seis tipos de gráfico, cada um com regra própria de quando usar:

- **Barra** — comparação entre categorias (padrão)
- **Pizza** — distribuição proporcional
- **Linha** — série temporal
- **Velocímetro (gauge)** — um indicador isolado, sem comparação
- **Combo** — PAT vs PDI da mesma ação lado a lado
- **Empilhado** — distribuição de status (zeradas/andamento/concluídas) entre poucos departamentos

O painel gerado fica salvo na Home do BICentral, junto dos painéis de Power BI — não é descartável.

## Outras capacidades (fora das ferramentas de dados)

- **Memória por conversa** — cada sessão de chat tem histórico próprio, retomável, renomeável, fixável e compartilhável por link público.
- **Identidade institucional própria** — sabe explicar quem o criou (equipe da PROAP/UFT), a origem do nome, e **nunca revela qual provedor de LLM está por trás**, mesmo se perguntado diretamente.
- **Consciência de limitação de dados** — sabe dizer explicitamente que o PDI ainda não está integrado quando a pergunta exige (em vez de inventar ou confundir com dado do PAT).
- **Distinção UA vs UG** — nunca mistura Unidade Acadêmica com Unidade Gestora numa mesma resposta.
- **Botão de parar** — qualquer geração em andamento pode ser interrompida pelo usuário, com cancelamento de verdade no backend (não só esconde a espera).
- **Orçamento por tester** — durante o período de teste, cada tester tem limite de uso individual visível na própria tela do chat.
- **Notificações proativas** — alerta sozinho quando um departamento está com execução muito baixa ou muito alta, sem precisar ser perguntado.

## Em números

- **19** ferramentas de consulta estruturada ao banco
- **6** tipos de gráfico diferentes
- **2** sistemas de geração de relatório (DOCX completo + resposta de chat)
- **1** sistema de busca semântica (RAG) sobre documentos institucionais
- Cobre **PDI, PAT, tarefas individuais e relatórios** — os quatro níveis de granularidade dos dados da PROAP, do mais agregado (eixo estratégico) ao mais específico (uma tarefa de uma pessoa)
