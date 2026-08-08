-- Sprint Tracker do BICentral — schema + RLS + seed
-- Rode isso inteiro no SQL Editor do Supabase (mesmo projeto do BICentral).
-- Cria tabelas novas, isoladas do resto do app — não mexe em nada existente.

create table if not exists sprint_tracker_sprints (
  id serial primary key,
  numero int not null,
  nome text not null,
  data_inicio date not null,
  data_fim date not null
);

create table if not exists sprint_tracker_tarefas (
  id serial primary key,
  sprint_id int references sprint_tracker_sprints(id),
  titulo text not null,
  descricao text,
  contexto text,
  passos jsonb not null default '[]',
  arquivos jsonb not null default '[]',
  dono text not null check (dono in ('dallyla','neci','lean','todos')),
  status text not null default 'a_fazer' check (status in ('a_fazer','fazendo','concluido')),
  depende_de int references sprint_tracker_tarefas(id)
);

alter table sprint_tracker_sprints enable row level security;
alter table sprint_tracker_tarefas enable row level security;

drop policy if exists "leitura publica sprints" on sprint_tracker_sprints;
create policy "leitura publica sprints" on sprint_tracker_sprints for select using (true);

drop policy if exists "leitura publica tarefas" on sprint_tracker_tarefas;
create policy "leitura publica tarefas" on sprint_tracker_tarefas for select using (true);

drop policy if exists "atualizar status" on sprint_tracker_tarefas;
create policy "atualizar status" on sprint_tracker_tarefas for update using (true) with check (true);

-- ============================================================
-- SEED
-- ============================================================

truncate table sprint_tracker_tarefas restart identity cascade;
truncate table sprint_tracker_sprints restart identity cascade;

insert into sprint_tracker_sprints (id, numero, nome, data_inicio, data_fim) values
  (1, 1, 'Fundação',                 '2026-08-07', '2026-08-20'),
  (2, 2, 'Tools + backend do ranking', '2026-08-21', '2026-09-03'),
  (3, 3, 'Integração',                '2026-09-04', '2026-09-17'),
  (4, 4, 'Polimento e buffer',        '2026-09-18', '2026-10-01');

insert into sprint_tracker_tarefas (id, sprint_id, titulo, descricao, contexto, passos, arquivos, dono, depende_de) values

(1, 1, 'Gatear RAG pro admin master',
 'Restringir quando a busca em documentos embutidos (RAG) roda, deixando ligada só quando quem pergunta é a Dallyla.',
 'RAG (Retrieval-Augmented Generation) é a parte que, antes de responder, busca trechos de documentos (ex: planilhas) parecidos com a pergunta e manda isso como contexto extra pro modelo de IA. Hoje isso roda em <strong>toda</strong> pergunta e por isso aparece "BASEADO EM: arquivo.xlsx" em toda resposta, mesmo quando não foi usado de verdade — porque o agente principal (AgenteConsultaSql) já responde a maioria das coisas direto do banco de dados, sem precisar de RAG. A forma exata de limitar isso ainda está em aberto — decidir com a Dallyla antes de codar.',
 '["Abrir ProiapController.java e adicionar @AuthenticationPrincipal UserDetails no método fazerPergunta.","Resolver o usuário logado com usuarioService.buscarPorEmail(userDetails.getUsername()).","Definir a regra de quem conta como \"admin master\": qualquer pessoa em admins_sistema, ou só um usuário fixo.","Passar esse resultado (um boolean) pra ProiapService.processarPergunta como novo parâmetro.","Em ProiapService.java, só chamar embeddingService.buscarContextoSemelhante(...) quando o parâmetro for verdadeiro; caso contrário, montar um ContextoRAG vazio.","Testar com dois logins diferentes: um autorizado e um comum."]',
 '[{"path":"backend/.../controller/ia/ProiapController.java","novo":false},{"path":"backend/.../service/ia/ProiapService.java","novo":false},{"path":"backend/.../service/ia/EmbeddingService.java","novo":false},{"path":"backend/.../service/admin/AdminService.java","novo":false}]',
 'dallyla', null),

(2, 1, 'Reconciliar ferramentas de ranking',
 'ConsultaAcoesTool e DesempenhoDepartamentoTool têm métodos quase idênticos de ranquear departamento por execução do PAT.',
 '"Tools" são métodos Java com a anotação @Tool que o agente de IA pode chamar sozinho no meio de uma conversa. Essas duas classes parecem ter sobrado de uma refatoração que não terminou. Escolher uma fonte única de verdade agora evita duplicar a lógica de novo quando o painel de ranking (Sprint 2) for construído em cima disso.',
 '["Abrir ConsultaAcoesTool.java e DesempenhoDepartamentoTool.java lado a lado.","Listar os métodos duplicados.","Escolher qual classe fica como fonte única (sugestão: ConsultaAcoesTool).","Apagar os métodos duplicados da outra classe; se ela ficar vazia, remover a classe.","Recompilar e testar uma pergunta que use ranking pra confirmar que nada quebrou."]',
 '[{"path":"backend/.../service/ia/ConsultaAcoesTool.java","novo":false},{"path":"backend/.../service/ia/DesempenhoDepartamentoTool.java","novo":false},{"path":"backend/.../config/LangchainConfig.java","novo":false}]',
 'dallyla', null),

(3, 1, 'Auditar convites e membros',
 'Revisar o fluxo de convite → aceite → entrada na equipe de ponta a ponta.',
 'O caminho é: EquipeController cria a equipe → ConviteController manda o convite → a pessoa convidada abre o link e cai na tela aceitar-convite → ao aceitar, vira uma linha em MembroEquipe.',
 '["Mapear o fluxo completo lendo EquipeController → ConviteController → ConviteEquipeService → tela aceitar-convite.","Testar manualmente: criar uma equipe, convidar um e-mail, aceitar o convite, confirmar que a pessoa aparece como membro.","Checar casos de borda: convite duplicado, convite expirado, aceitar com e-mail diferente do convidado.","Anotar qualquer bug encontrado."]',
 '[{"path":"backend/.../controller/equipe/ConviteController.java","novo":false},{"path":"backend/.../controller/equipe/EquipeController.java","novo":false},{"path":"backend/.../service/equipe/ConviteEquipeService.java","novo":false},{"path":"frontend/src/app/aceitar-convite/","novo":false},{"path":"frontend/src/app/equipe/","novo":false}]',
 'neci', null),

(4, 1, 'Estabilizar painel-admin',
 'Telas de gestão de admins, APIs, gerentes de departamento e configurações de notificação.',
 'São 4 telas independentes dentro de /admin, cada uma com seu próprio CRUD simples. Todas chamam AdminController/AdminService no backend.',
 '["Abrir cada sub-tela e testar o CRUD completo.","Conferir se erro de validação do backend aparece de forma legível.","Padronizar loading/erro entre as 4 telas se estiverem inconsistentes."]',
 '[{"path":"frontend/src/app/painel-admin/gestao-admins.component.ts","novo":false},{"path":"frontend/src/app/painel-admin/gestao-gerentes.component.ts","novo":false},{"path":"frontend/src/app/painel-admin/gestao-apis.component.ts","novo":false},{"path":"frontend/src/app/painel-admin/configuracoes-notificacao.component.ts","novo":false},{"path":"backend/.../controller/admin/AdminController.java","novo":false}]',
 'neci', null),

(5, 1, 'Ajuste fino de notificações',
 'Sistema já está bom — só polimento pontual, sem redesenho.',
 'As notificações vêm de NotificacaoService.gerarNotificacoes e aparecem como cards recolhíveis no sino do agente.',
 '["Usar o sistema por alguns dias e listar qualquer irritação pequena.","Ajustar só o que foi listado — não redesenhar do zero.","Se aparecer algo que parece bug, tratar como card separado."]',
 '[{"path":"backend/.../service/notificacao/NotificacaoService.java","novo":false},{"path":"frontend/src/app/agent/agent.component.ts","novo":false}]',
 'lean', null),

(6, 1, 'Prototipar painel de ranking',
 'Decidir a abordagem de animação (biblioteca, layout) enquanto o backend do Sprint 2 ainda não existe.',
 'A VISÃO COMPLETA (da Dallyla, pra não se perder no meio das sprints): um painel geral, visual e futurista, que mostra TODAS as UGs (unidades gestoras/departamentos) de uma vez, ranqueadas por desempenho — não é um gráfico de barra comum, é pensado pra impressionar. O ponto central é a ANIMAÇÃO: quando uma UG melhora de desempenho, ela literalmente sobe de posição na tela, visivelmente, na frente do usuário — não é só atualizar um número, é a linha/card dela se movendo pra cima na lista. E esse painel não fica escondido em menu nenhum: ele aparece na TELA DE ENTRADA, quando a pessoa abre o agente proIAp — é a primeira coisa que ela vê. Esse card (Sprint 1) é só decidir COMO fazer a animação de reordenação antes de ter dado real pra testar — não precisa ficar bonito ainda, só validar a mecânica.',
 '["Avaliar se dá pra reaproveitar ngx-echarts (já usado em grafico-ia) ou se precisa de Angular Animations pra mover cards de posição suavemente.","Fazer um mock estático (lista fixa de departamentos com posição inventada) só pra validar a animação de troca de posição — sem precisar de backend ainda.","Pensar no visual \"futurista\": cores, gradientes, talvez algo tipo leaderboard de jogo — não precisa ser polido agora, só ter a direção.","Alinhar com a Dallyla o formato de dado que o endpoint do Sprint 2 vai devolver."]',
 '[{"path":"frontend/src/app/grafico-ia/grafico-ia.ts","novo":false},{"path":"frontend/src/app/dashboard/dashboard.component.ts","novo":false}]',
 'lean', null),

(7, 2, 'Gráfico como @Tool',
 'Transformar a geração de gráfico numa @Tool comum, chamável livremente pelo AgenteConsultaSql.',
 'Hoje gerar gráfico é um fluxo separado e rígido: classifica a pergunta, gera uma proposta, guarda como pendente, e só mostra depois de confirmação. A ideia é acabar com esse vaivém.',
 '["Ler AgenteProiap.gerarGrafico e entender o fluxo atual de confirmação.","Criar um método @Tool novo que devolve GraficoSpec pronto, sem confirmação.","Remover ou manter como fallback a ramificação separada de intenção GRAFICO.","Testar pedindo um gráfico direto no chat."]',
 '[{"path":"backend/.../service/ia/AgenteConsultaSql.java","novo":false},{"path":"backend/.../service/ia/AgenteProiap.java","novo":false},{"path":"backend/.../dto/painel/GraficoSpec.java","novo":false}]',
 'dallyla', null),

(8, 2, 'Ativar PainelSpec',
 'DTO que já existe pra agrupar vários gráficos num só "painel", mas nenhum código usa ele hoje.',
 'PainelSpec é {título, lista de GraficoSpec} — ficou esboçado e nunca foi ligado a nada.',
 '["Criar um método @Tool que devolve PainelSpec em vez de um GraficoSpec único.","No frontend, decidir onde renderizar uma lista de gráficos.","Testar os dois casos lado a lado: pedido amplo vs pedido específico."]',
 '[{"path":"backend/.../dto/painel/PainelSpec.java","novo":false},{"path":"backend/.../dto/painel/GraficoSpec.java","novo":false}]',
 'dallyla', null),

(9, 2, 'Endpoint do ranking geral',
 'Endpoint novo que devolve o ranking das UGs em JSON puro, sem passar pelo modelo de IA.',
 'Esse endpoint é a base de dados de todo o painel futurista que o Lean está construindo (ver contexto do card "Prototipar painel de ranking"). Importante: tem que trazer TODAS as UGs, não uma amostra — a visão da Dallyla é o painel mostrar o ranking completo. Fonte de dado: pat_dados (via a query reconciliada no Sprint 1), NÃO pat_tarefas — pat_dados tem o % de execução por departamento, que é o número que vira posição no ranking; pat_tarefas é tarefa operacional individual, serve pra outra coisa (notificação de atrasos). O endpoint só faz SELECT, nunca escreve em pat_dados — quem escreve ali é só o job de sincronização diário (SincronizacaoPatJob), escrever por fora bagunçaria o próximo sync. Diferente das tools de ranking (que devolvem texto pro LLM formatar), esse endpoint precisa devolver dado estruturado e rápido, porque o painel carrega assim que a pessoa abre o agente. Atenção: já existe PainelController.java, mas é de outra coisa (embed do Power BI) — não misturar.',
 '["Criar um controller novo (ex: RankingController.java).","Reaproveitar a query SQL já validada na ferramenta reconciliada do Sprint 1.","Garantir que traz TODAS as UGs, não um top N.","Formato sugerido: lista de {departamento, percentualExecucao, posicaoAtual}.","Testar isolado via curl/Postman antes do Lean integrar."]',
 '[{"path":"backend/.../controller/painel/RankingController.java","novo":true}]',
 'dallyla', 2),

(10, 2, 'Modelo de snapshot diário',
 'Guardar uma "foto" do ranking a cada dia pra dar pra comparar e animar quem subiu/desceu.',
 'Essa é a peça que dá dado real pra ANIMAÇÃO CENTRAL do painel do Lean: sem saber a posição de ontem, não dá pra saber se a UG subiu ou desceu hoje, e a animação de "subir na lista" é o coração da ideia da Dallyla pro painel. Precisa persistir um snapshot diário, no espírito dos jobs de sincronização que já existem.',
 '["Criar uma tabela nova (ranking_snapshot).","Criar um job agendado novo (padrão de SincronizacaoPatJob).","Incluir a posição de ontem no endpoint do ranking geral, pra o frontend saber se anima pra cima ou pra baixo."]',
 '[{"path":"backend/.../job/SincronizacaoPatJob.java","novo":false},{"path":"backend/.../job/RankingSnapshotJob.java","novo":true}]',
 'dallyla', 9),

(11, 2, 'Aplicar os achados da auditoria do Sprint 1',
 'Corrigir de verdade o que a auditoria de convites/painel-admin encontrou — não é "resolver bug genérico", é fechar os casos de borda específicos.',
 'No Sprint 1 você audita e lista os problemas (card "Auditar convites e membros" e "Estabilizar painel-admin"); aqui você efetivamente corrige. Exemplos concretos do que costuma aparecer nesse tipo de auditoria: convite duplicado não tratado, convite expirado ainda aceito, mensagens de erro do backend aparecendo cruas na tela em vez de traduzidas, loading/erro inconsistente entre as 4 telas do painel-admin.',
 '["Pegar a lista real de problemas anotada no Sprint 1 (não uma lista genérica).","Corrigir validação de casos de borda no fluxo de convite (duplicado, expirado, e-mail divergente).","Padronizar mensagens de erro e estado de loading nas 4 telas do painel-admin.","Testar cada correção manualmente antes de marcar como concluído."]',
 '[{"path":"backend/.../controller/equipe/ConviteController.java","novo":false},{"path":"backend/.../service/equipe/ConviteEquipeService.java","novo":false},{"path":"frontend/src/app/painel-admin/","novo":false}]',
 'neci', null),

(12, 2, 'Onboarding de novo admin',
 'Fluxo de convite/primeiro acesso pra alguém virar admin do sistema, sem precisar de alguém mexendo direto na tela.',
 'Hoje só existe um admin fixo (bootstrap) e adicionar outros é manual, direto na tela gestao-admins. Isso já estava no roadmap de setembro da Dallyla — vira uma feature de verdade: convite por e-mail, aceite, primeiro acesso guiado.',
 '["Desenhar o fluxo: quem pode convidar, como o convite chega, o que a pessoa vê no primeiro acesso.","Reaproveitar o padrão de convite que já existe em equipe (ConviteEquipeService) como referência, se fizer sentido.","Construir o endpoint + tela de aceite.","Testar de ponta a ponta com um usuário novo de verdade."]',
 '[{"path":"backend/.../controller/admin/AdminController.java","novo":false},{"path":"backend/.../service/admin/AdminService.java","novo":false},{"path":"backend/.../service/equipe/ConviteEquipeService.java (referência de padrão)","novo":false},{"path":"frontend/src/app/painel-admin/gestao-admins.component.ts","novo":false}]',
 'neci', null),

(13, 2, 'Componente do painel de ranking',
 'Construir o componente visual com dado mockado até o endpoint ficar pronto.',
 'Aqui é onde a visão vira componente de verdade: um painel mostrando TODAS as UGs ranqueadas, visual futurista (não é uma tabela simples), pronto pra receber a animação de reordenação que foi prototipada no card do Sprint 1. Pensa nisso como uma tela de destaque, não um widget pequeno — ela vai ser a "cara" de entrada do agente.',
 '["Criar componente novo (painel-ranking/), inspirado em grafico-ia.ts.","Usar dado mockado no formato combinado com a Dallyla, com TODAS as UGs (não um recorte pequeno, pra já testar como fica com a lista cheia).","Implementar a animação de reordenação em cima desse mock, usando a abordagem decidida no protótipo do Sprint 1.","Cuidar do visual: essa é a tela de entrada do agente, merece acabamento."]',
 '[{"path":"frontend/src/app/grafico-ia/grafico-ia.ts","novo":false},{"path":"frontend/src/app/painel-ranking/","novo":true}]',
 'lean', 6),

(14, 3, 'Testar gráfico/painel ponta a ponta',
 'Validar o agente gerando gráfico e painel sob demanda em cenários reais.',
 null,
 '["Testar gráfico simples, painel amplo, e pergunta comum separadamente.","Testar pedidos ambíguos.","Registrar caso estranho como bug."]',
 '[{"path":"frontend/src/app/agent/agent.component.ts","novo":false},{"path":"backend/.../service/ia/ProiapService.java","novo":false}]',
 'dallyla', 7),

(15, 3, 'Apoiar integração do ranking',
 'Suporte técnico pro Lean plugar o painel de ranking na tela de entrada do agente.',
 null,
 '["Revisar com o Lean o contrato do endpoint.","Resolver dúvidas de formato de dado.","Ajustar o endpoint se necessário."]',
 '[{"path":"backend/.../controller/painel/RankingController.java","novo":false}]',
 'dallyla', 9),

(16, 3, 'Testar onboarding de admin com usuário real',
 'Validar o fluxo de convite/primeiro acesso de admin (Sprint 2) com uma pessoa de verdade tentando pela primeira vez, não só você testando o que você mesma construiu.',
 'Quem constrói um fluxo de convite tende a não ver os pontos confusos que uma pessoa nova vê. Pedir pro Lean ou pra Dallyla (que não construíram isso) passar pelo fluxo do zero, sem ajuda, e anotar onde travaram ou ficaram em dúvida.',
 '["Pedir pra alguém do time que não construiu o fluxo testar o onboarding de admin do início ao fim, sem dica.","Anotar cada ponto de confusão ou travamento.","Corrigir os problemas encontrados.","Só depois disso, fechar qualquer pendência residual dos sprints 1 e 2."]',
 '[{"path":"backend/.../controller/admin/AdminController.java","novo":false},{"path":"frontend/src/app/painel-admin/gestao-admins.component.ts","novo":false}]',
 'neci', 12),

(17, 3, 'Decidir onde o ranking mora na navegação',
 'Escolher a tela de entrada certa pro painel de ranking aparecer.',
 'A Dallyla foi específica: o painel deve aparecer "quando eu entro no agente de início" — ou seja, na tela do próprio agente proIAp (frontend/src/app/agent/), como uma espécie de landing/estado inicial antes ou acima do chat, não escondido num menu separado. dashboard.component.ts é uma opção (hoje só tem um handler de logout, praticamente vazio), mas confirmar com ela se a intenção é literalmente dentro da tela do agente ou uma tela própria que vem antes dele.',
 '["Confirmar com a Dallyla: o painel entra dentro da tela do agente (frontend/src/app/agent/) ou numa tela separada tipo dashboard.component.ts?","Criar/ajustar a rota do Angular de acordo com a decisão.","Confirmar que o painel carrega rápido (sem passar por LLM) assim que a pessoa entra."]',
 '[{"path":"frontend/src/app/dashboard/dashboard.component.ts","novo":false},{"path":"frontend/src/app/agent/agent.component.ts","novo":false}]',
 'neci', null),

(18, 3, 'Terminar animação de posição',
 'UG subindo ou descendo no ranking, de forma visível e fluida.',
 'Essa é a parte que faz o painel ser "futurista" de verdade em vez de só uma lista — é o efeito visual que a Dallyla descreveu desde o início: a UG se movendo pra cima na tela quando melhora. Vale caprichar aqui, é o diferencial do painel.',
 '["Refinar a transição visual (suavidade, duração, seta verde/vermelha ou indicador equivalente).","Testar em telas menores e temas claro/escuro.","Conferir que dá pra perceber a mudança de posição mesmo sem prestar atenção total na tela — o efeito precisa chamar atenção."]',
 '[{"path":"frontend/src/app/painel-ranking/","novo":false}]',
 'lean', 13),

(19, 3, 'Integração real com o endpoint',
 'Trocar o dado mockado do Sprint 2 pelo endpoint de verdade.',
 'Ponto de atenção: o endpoint (card do Sprint 2) tem que trazer TODAS as UGs — se ao integrar aparecer só um recorte, é bug, não é o painel completo que foi pedido.',
 '["Substituir o array mockado pela chamada HTTP real.","Confirmar que TODAS as UGs aparecem, não só as que estavam no mock.","Tratar estado de carregamento e erro.","Confirmar que a animação continua funcionando com dado real."]',
 '[{"path":"frontend/src/app/painel-ranking/","novo":false},{"path":"backend/.../controller/painel/RankingController.java","novo":false}]',
 'lean', 9),

(20, 4, 'Bug bash cruzado', 'Time inteiro testando as 3 frentes junto.', null, '[]', '[]', 'todos', null),
(21, 4, 'Testes de regressão nas 3 frentes', 'Confirmar que nada do que já funcionava quebrou.', null, '[]', '[]', 'todos', null),
(22, 4, 'Preparar demonstração', 'Roteiro e ambiente prontos pra apresentar o estágio.', null, '[]', '[]', 'todos', null),

(23, 1, 'Revisão de segurança geral',
 'Checar se existe mais algum vazamento tipo o que achamos e corrigimos nessa sessão.',
 'A view pat_execucao_departamento estava rodando como "security definer" e furando o RLS das tabelas por trás, vazando dado publicamente pela API anônima do Supabase — já corrigido (ALTER VIEW ... SET (security_invoker = true)). Não tem garantia de que não existe mais alguma tabela/view com o mesmo problema.',
 '["Listar todas as tabelas e views do projeto no Supabase.","Confirmar RLS ligado em cada tabela (não só as novas).","Procurar outras views que possam estar rodando como security definer.","Testar acesso anônimo em cada uma via curl (mesmo padrão usado nessa sessão) e confirmar que só retorna o que deveria."]',
 '[]',
 'dallyla', null),

(24, 3, 'Polir relatório: aparência + cooldown',
 'Melhorar a aparência visual do DOCX/PDF gerado e adicionar um cooldown deliberado entre relatórios (controle de custo de token).',
 'A aparência é código Java (Apache POI pro DOCX, PDFBox pro PDF) em RelatorioService.java — já foi reformulado estruturalmente essa sessão, mas ainda pode melhorar espaçamento/tipografia/hierarquia visual. O cooldown é outra coisa: hoje só existe uma trava de 60s contra duplicata acidental (idempotência), não um cooldown deliberado pra evitar gerar relatório demais e gastar token à toa — isso ainda não foi construído.',
 '["Revisar o DOCX/PDF gerado hoje e listar o que incomoda visualmente.","Ajustar gerarDocx/gerarPdf (espaçamento, fontes, hierarquia).","Decidir o tempo do cooldown e onde aplicar (provavelmente solicitarRelatorio).","Trocar o bloqueio silencioso por uma mensagem clara tipo \"aguarde X min antes do próximo relatório\"."]',
 '[{"path":"backend/.../service/ia/RelatorioService.java","novo":false},{"path":"backend/.../service/ia/RelatorioContextoTool.java","novo":false}]',
 'dallyla', null),

(25, 4, 'Decidir e configurar hospedagem',
 'Hoje não existe deploy nenhum — o sistema inteiro só roda em localhost.',
 'Pra ser um MVP que a pró-reitoria realmente acessa (não só vocês três no próprio computador), precisa estar hospedado em algum lugar acessível pela internet.',
 '["Escolher host pro backend (Spring Boot precisa de host Java, ex: Railway/Render/Fly.io).","Escolher host pro frontend (Angular estático, ex: Vercel/Netlify).","Configurar variáveis de ambiente em produção (chaves de API, banco).","Testar o sistema inteiro ponta a ponta com as URLs reais de produção."]',
 '[]',
 'todos', null);

select setval('sprint_tracker_tarefas_id_seq', (select max(id) from sprint_tracker_tarefas));
select setval('sprint_tracker_sprints_id_seq', (select max(id) from sprint_tracker_sprints));
