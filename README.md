<div align="center">

<img src="docs/assets/proiap-logo.svg" alt="proIAp" width="260" />

### Hub de painéis Power BI e agente de IA institucional para a PROAP/UFT

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)](backend)
[![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)](frontend)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](backend)
[![Postgres](https://img.shields.io/badge/Postgres-Supabase-3ECF8E?logo=supabase&logoColor=white)](https://supabase.com)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-todos%20os%20direitos%20reservados-B22222)](LICENSE)

</div>

---

## O que é o BICentral

O BICentral é a plataforma interna da PROAP (Pró-Reitoria de Avaliação e Planejamento) da UFT para centralizar painéis Power BI e dar acesso rápido, conversacional, aos dados do PAT (Plano Anual de Trabalho) e do PDI (Plano de Desenvolvimento Institucional) — sem precisar garimpar planilha.

O projeto nasceu como hub de painéis e cresceu para incluir o **proIAp**, o agente de IA que é hoje o coração do sistema.

## proIAp — o agente

O proIAp responde perguntas sobre o PAT/PDI combinando duas fontes:

- **Consulta estruturada** — ferramentas que consultam o banco direto (tarefas do usuário logado, ranking de execução por departamento, relatórios de desempenho), sempre com números reais, nunca inventados.
- **Busca semântica (RAG)** — sobre documentos institucionais enviados pela equipe (PDFs, planilhas, normativas), disponível para administradores.

Ele também gera relatórios de desempenho em DOCX/PDF sob demanda e mantém um painel de notificações de atraso por departamento.

## Funcionalidades

- 📊 **Painéis Power BI** — cadastro, edição e captura automática de capa por usuário.
- 🤖 **proIAp** — chat com o agente, tarefas pessoais, ranking de departamentos, relatórios automáticos.
- 🔔 **Notificações** — alertas de rendimento e atraso por departamento, com painel detalhado.
- 🛡️ **Painel admin** — gestão de equipes, gerentes, responsáveis e ingestão de documentos para o RAG.
- 🔐 **Autenticação** — cadastro/login com JWT e verificação de e-mail.

## Screenshots

<div align="center">
<img src="docs/assets/screenshot-chat.png" alt="Chat do proIAp" width="49%" />
<img src="docs/assets/screenshot-home.png" alt="Home com os painéis Power BI" width="49%" />
</div>

## Tecnologias

**Backend**: Spring Boot 3.4, Java 17, Spring Security (JWT), langchain4j (Groq/Gemini/Cerebras/SambaNova/Ollama), Postgres via Supabase, JdbcTemplate.

**Frontend**: Angular 20, standalone components.

## Equipe

Desenvolvido por estagiários da PROAP, estudantes de Ciência da Computação da UFT:

- **Dallyla de Moraes Sousa** 
- **Lean de Albuquerque Pereira**
- **Neci Mendes Fialho**

## Como rodar localmente

Backend:
```bash
cd backend
./mvnw spring-boot:run
```

Frontend:
```bash
cd frontend
npm install
npm start
```

## Estrutura

- `backend/` — API e serviços: autenticação, painéis, proIAp (agentes de IA, tools, RAG), admin, notificações, ranking.
- `frontend/` — UI Angular: login, cadastro, home, chat do proIAp, painel admin.
- `docs/` — documentação complementar.

## Observações

- O link do Power BI precisa começar com `https://app.powerbi.com/view?r=`.
- A captura da capa dos painéis roda de forma assíncrona para não travar o cadastro.

## Licença e uso

> [!IMPORTANT]
> **Este código não é de uso livre.** O repositório é público para consulta, mas não tem licença de código aberto. Copiar, modificar, redistribuir ou reaproveitar o código, os prompts ou o agente proIAp exige autorização prévia da UFT.

<table>
<tr>
<th width="50%">🔓 Dados: públicos</th>
<th width="50%">🔒 Sistema: protegido</th>
</tr>
<tr>
<td valign="top">

As informações do **PAT** e do **PDI** consultadas pelo BICentral são dados institucionais públicos da UFT. Elas podem ser obtidas nas fontes oficiais da universidade.

</td>
<td valign="top">

O **código-fonte**, o **agente proIAp**, seus **prompts** e suas **ferramentas** pertencem à Universidade Federal do Tocantins, conforme a Lei nº 9.609/1998, art. 4º.

</td>
</tr>
</table>

| Você pode | Você não pode, sem autorização |
| --- | --- |
| ✅ Ler e estudar o código aqui no GitHub | ❌ Copiar o código para outro projeto |
| ✅ Citar o projeto em trabalhos acadêmicos | ❌ Reaproveitar os prompts ou o agente proIAp |
| ✅ Usar os dados públicos do PAT e do PDI | ❌ Redistribuir, modificar ou vender o sistema |

**Autoria reconhecida** da equipe de desenvolvimento listada acima, nos termos do art. 2º, § 1º da Lei nº 9.609/1998. Termos completos no arquivo [LICENSE](LICENSE). Pedidos de autorização: PROAP/UFT.

---

<div align="center">
<sub>© 2025–2026 Universidade Federal do Tocantins · Desenvolvido na PROAP · Todos os direitos reservados</sub>
</div>
