# auditoria.md

**Feature:** `feature/auditoria-convites-membros`
**Escopo:** fluxo de convite → aceite → entrada na equipe

---

## Resumo

[Alguns bugs encontrados que serão listados a seguir, além de problemas gerais de funcionamento que também serão listados.]

---

### [Bug 1] Aceite de convite não validava quem estava logado

- **Origem:** análise de código (`ConviteEquipeService.aceitarConvite` + `SecurityConfig`)
- **Descrição:** endpoint `/api/convites/aceitar` era público e nunca comparava
  o e-mail do convite com o usuário autenticado.

### [Bug 2] Reenvio de convite invalida token antigo silenciosamente

- **Origem:** análise de código
- **Descrição:** reenviar convite pro mesmo e-mail reaproveita a linha com
  novo token; o link antigo (já enviado por e-mail) passa a dar 404.

### [Bug 3] Sobrescrita silenciosa de role ao aceitar convite antigo

- **Origem:** análise de código
- **Descrição:** se alguém for adicionado direto à equipe (`adicionarMembro`)
  e depois aceitar um convite `PENDENTE` antigo, o role é sobrescrito sem aviso.

### [Bug 4] Possível corrida em double-submit de convite

- **Origem:** análise de código
- **Descrição:** duplo clique rápido no botão de convite pode gerar dois
  registros `PENDENTE` para o mesmo e-mail+equipe.

### [Bug 5] Exceções inconsistentes entre EquipeService e ConviteEquipeService

- **Origem:** análise de código
- **Descrição:** `adicionarMembro`/`listarMembros` usam `RuntimeException`
  genérica (vira 500) em vez de exceções estruturadas como o resto da API.

### [Bug 6] Seleção de equipe no localStorage não é escopada por usuário

- **Origem:** análise de código (`equipe.component.ts`)
- **Descrição:** chave fixa no `localStorage`, sem vínculo com o usuário logado.
- **Status:** não corrigido

### [Manuais] Testes sem erro aparente, mas são erros sim
- token de verificação null no banco de dados após cadastro e verificação
- login feito com duas contas diferentes (ambos adm) e não foi apresentado painéis de forma alguma. Logo após login feito com uma conta recém cadastrada (não adm) e conta tinha acesso à equipes auditoria e orçamento (sem fazer parte delas).
- conta comum com acesso a pag de gerenciamento dessas equipes
- conta comum não cria ou exclui equipe (mensagens de erro genéricas. se for manter o acesso de conta comum a gerenciamento, deixar claro nas mensagens de erro o porque de ela não conseguir concluir essas ações)
- conta comum conseguiu enviar convite de equipe a outro usuario (inclusive convite para ser adm) sem ao menos ser parte da equipe (mas tendo acesso ao gerenciamente da dita equipe)
- conta comum conseguiu remover usuário da equipe sem ao menos ser parte da equipe. o usuário removido era adm
- possibilidade de add membros repetidos no msm departamento e UG
- usuário não foi verificado então não consegue logar, apesar disso, foi listado como usuario e pode ser adicionado como gerente de departamento
- usuario não verificado pôde ser adicionado como adm
- conta não verificada e não consegue verificar de forma alguma; provavelmente convite expirou, mas não há mensagem sinalizando isso. conta não consegue cadastrar novamente pelo problema anterior de já estar cadastrada (no caso listada como cadastrada), e não consegue logar por não ser verificada.
- acesso a pag adm sem ser adm
