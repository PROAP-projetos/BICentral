# Projeto: Sistema de Gestão de Painéis (BICentral)

## 📋 Sobre o Projeto

**BICentral** é um sistema de gestão de painéis (dashboards) de Business Intelligence que permite equipes organizarem e compartilharem links de painéis analíticos de forma centralizada e segura.

### O que é este projeto?

Este é um projeto acadêmico/empresarial desenvolvido para facilitar o gerenciamento e visualização de painéis de BI por múltiplas equipes. O sistema oferece:

- **Centralização de Painéis**: Organize todos os links dos painéis de BI da sua organização em um único lugar
- **Gestão de Equipes**: Crie equipes e controle quem pode visualizar ou editar cada painel
- **Controle de Acesso**: Sistema de permissões com roles (Admin, Editor, Viewer)
- **Autenticação Segura**: Login com JWT e gerenciamento de senhas
- **Interface Responsiva**: Acesso via desktop e mobile
- **Auditoria**: Registro de todas as ações importantes do sistema

### 🛠️ Stack Tecnológica

#### Backend
- **Framework**: Spring Boot 3.5.6
- **Linguagem**: Java 17
- **Banco de Dados**: PostgreSQL
- **Segurança**: Spring Security + JWT
- **ORM**: Spring Data JPA
- **Build Tool**: Maven

#### Frontend (Planejado)
- **Framework**: Angular
- **UI**: Design responsivo com categorização visual

### 🎯 Objetivo

Facilitar o acesso e a gestão de painéis de Business Intelligence para equipes, permitindo que gestores organizem painéis por categoria e controlem as permissões de visualização e edição de acordo com o papel de cada membro da equipe.

### 👥 Público-Alvo

- Gestores de BI que precisam organizar e compartilhar painéis com suas equipes
- Analistas de dados que precisam acesso rápido aos painéis relevantes
- Administradores que precisam controlar permissões e auditar acessos

---

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

---

## 📁 Estrutura do Projeto

```
BICentral/
├── backend/              # API REST em Spring Boot
│   ├── src/             # Código-fonte Java
│   ├── pom.xml          # Dependências Maven
│   └── ...
├── frontend/            # Interface Angular (a ser implementado)
├── README.md            # Este arquivo
└── ...
```

## 🚀 Como Executar

### Pré-requisitos
- Java 17 ou superior
- Maven 3.6+
- PostgreSQL
- Node.js e Angular CLI (para o frontend, quando implementado)

### Backend

1. Configure o banco de dados PostgreSQL
2. Atualize as configurações em `backend/src/main/resources/application.properties`
3. Execute:

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

O frontend em Angular ainda está em desenvolvimento.

## 📝 Status do Projeto

Este projeto está em desenvolvimento ativo. As histórias de usuário listadas acima representam o roadmap completo do sistema. O backend Spring Boot está em implementação, e o frontend Angular será desenvolvido posteriormente.

## 📖 Documentação Adicional

- [ARTIGO_GIT_CONECTIVIDADE.md](ARTIGO_GIT_CONECTIVIDADE.md) - Artigo sobre conectividade com Git
- [RESUMO_RAPIDO_GIT.md](RESUMO_RAPIDO_GIT.md) - Guia rápido de comandos Git

## 🤝 Contribuindo

Este é um projeto acadêmico/empresarial. Para contribuir, siga as práticas de desenvolvimento estabelecidas pela equipe.

## 📄 Licença

Este projeto é de propriedade da organização PROAP-projetos.
