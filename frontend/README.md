# Projeto: Sistema de Gestão de Painéis

## Épicos e Histórias de Usuário

### 🟣 Épico 1: Autenticação e Cadastro

#### US-001 – Cadastro de Usuário
**Como** usuário, **quero** criar minha conta **para** acessar o sistema.
- Criar tela de cadastro no Angular
- Implementar validação de formulário
- Criar endpoint de cadastro no Spring Boot (`POST /usuarios`)
- Salvar usuário no banco de dados
- Testes de integração (cadastro)

#### US-002 – Login com Email/Senha
**Como** usuário, **quero** fazer login com email/senha **para** acessar o sistema.
- Criar tela de login no Angular
- Implementar autenticação JWT no backend
- Endpoint de login (`POST /auth/login`)
- Guard no Angular para proteger rotas privadas
- Testes de login/logout

#### US-003 – Redefinição de Senha
**Como** usuário, **quero** redefinir minha senha **caso** eu esqueça.
- Tela de recuperação de senha
- Endpoint de reset no backend
- Integração com email (mock no início)
- Testes de fluxo de recuperação

### 🟣 Épico 2: Gestão de Painéis

#### US-004 – Cadastro de Painel
**Como** gestor, **quero** cadastrar um painel com título, descrição e link **para** minha equipe visualizar.
- Modelo Painel no banco (`id`, `título`, `descrição`, `link`, `equipeId`)
- Endpoint `POST /paineis`
- Tela Angular de cadastro de painel
- Validação de links

#### US-005 – Listagem de Painéis
**Como** gestor, **quero** listar todos os painéis da minha equipe em um overview categorizado.
- Endpoint `GET /paineis?equipeId=X`
- Tela Angular de overview com categorias
- Estilizar painéis em cards
- Implementar busca/filtro básico

#### US-006 – Edição de Painel
**Como** gestor, **quero** editar o link de um painel existente **sem precisar** recriá-lo.
- Endpoint `PUT /paineis/{id}`
- Botão “Editar” no Angular
- Atualizar link dinamicamente no front
- Testes de edição

#### US-007 – Exclusão de Painel
**Como** gestor, **quero** excluir um painel que não é mais usado.
- Endpoint `DELETE /paineis/{id}`
- Botão “Excluir” no Angular
- Confirmação antes da exclusão
- Testes de exclusão

### 🟣 Épico 3: Gestão de Equipes e Permissões

#### US-008 – Criação de Equipe
**Como** admin, **quero** criar uma equipe e adicionar membros **para** organizar os painéis.
- Modelo Equipe no banco (`id`, `nome`, `descrição`)
- Endpoint `POST /equipes`
- Tela Angular para criação de equipe
- Associação de usuários à equipe

#### US-009 – Atribuição de Papéis
**Como** gestor, **quero** atribuir papéis (viewer/editor) aos membros da minha equipe.
- Implementar papéis no banco (`role: viewer/editor/admin`)
- Endpoint `PUT /equipes/{id}/membros`
- Tela Angular para alterar permissões
- Regras de acesso aplicadas no backend

#### US-010 – Visualização Restrita de Painéis
**Como** viewer, **quero** visualizar apenas os painéis da minha equipe.
- Middleware no backend para restringir acesso
- Angular Guards para bloquear acesso de quem não tem permissão
- Testes de acesso negado

#### US-011 – Atualização de Painéis por Editores
**Como** editor, **quero** poder atualizar links de painéis da minha equipe.
- Endpoint respeitando permissões
- UI que mostra opções diferentes para viewer x editor
- Testes de permissão

### 🟣 Épico 4: Qualidade e Extras

#### US-012 – Experiência Responsiva
**Como** usuário, **quero** ter uma experiência agradável e responsiva.
- Layout responsivo em Angular
- Tema simples com categorias visuais
- Testar em desktop e mobile

#### US-013 – Registro de Logs
**Como** admin, **quero** registrar logs de ações (login, cadastro, edição de painel) **para** auditoria.
- Middleware no backend para logs
- Registro em tabela Logs (`ação`, `usuário`, `data`)
- Endpoint `GET /logs` (restrito a admin)
