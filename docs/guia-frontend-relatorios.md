# Guia: melhorar o frontend de relatórios do proIAp

Para a Neci. Explica como os relatórios funcionam hoje e onde estão os arquivos de front e de back.

---

## 1. Como um relatório nasce hoje (ponta a ponta)

1. A pessoa pede no **chat**: "gera um relatório da PROEST em Excel, com o nome da ação".
2. O agente de IA chama a ferramenta `solicitarGeracaoRelatorio` (backend), que grava uma linha em
   `relatorios_gerados` com `status = PROCESSANDO` e dispara a geração **assíncrona** (leva uns 20 a 30 s).
3. A resposta do chat volta com `relatorioGerado = true`. O front então **abre sozinho o painel "Meus Relatórios"**
   e começa a fazer **polling a cada 5 s** do histórico, enquanto houver algum relatório `PROCESSANDO`.
4. Quando o backend termina, a linha vira `PRONTO` (com `arquivo_url`) ou `ERRO` (com `mensagem_erro`).
5. No painel, a pessoa baixa o arquivo no formato original (DOCX, PDF ou XLSX) e/ou clica em **PDF** pra ver uma
   versão em PDF, que é gerada **sob demanda** a partir do JSON salvo (`POST /{id}/pdf`) e guardada em `pdf_url`.

Ou seja, **não existe formulário de "gerar relatório" no front**. Todo pedido passa pelo chat. O painel é só
o **histórico** (listar, baixar, ver PDF, excluir).

O conteúdo do documento (capa, visão executiva, pontos de atenção, tabelas etc.) é montado **em Java**
(`RelatorioService`), não no front. Mexer na aparência do DOCX, PDF ou Excel é trabalho de backend.

---

## 2. Onde estão os arquivos

### Frontend (`frontend/src/app/`)

| Arquivo | O que tem |
|---|---|
| `agent/agent.html` (linhas ~208 a 277) | Markup do botão de relatórios (ícone de documento no topo) e do painel **"Meus Relatórios"** (`relatorio-panel`) |
| `agent/agent.component.ts` | Toda a lógica do painel: `toggleRelatorio`, `abrirPainelRelatorios`, `carregarMeusRelatorios`, `iniciarPollingHistorico` (5 s), `pararPollingRelatorio`, `abrirPdfRelatorio`, `excluirRelatorio`, `paraDataUtc`. Busque por `relatorio` (cerca de 60 ocorrências) |
| `agent/agent.css` (linhas ~1550 a 1750) | Estilos do painel: `.relatorio-panel`, `.relatorio-historico-item`, `.relatorio-status-badge`, `.relatorio-action-group`, `.relatorio-pdf-btn`, `.relatorio-delete-btn` |
| `services/agent.service.ts` | Chamadas HTTP e interfaces: `RelatorioHistoricoItem`, `RelatorioStatusResponse`, `RelatorioPdfResponse`, métodos `listarMeusRelatorios`, `gerarPdfRelatorio`, `excluirRelatorio` |

### Backend (`backend/src/main/java/com/bicentral/bicentral_backend/`)

| Arquivo | O que tem |
|---|---|
| `controller/ia/RelatorioController.java` | Endpoints REST em `/api/proiap/relatorio` |
| `service/ia/RelatorioService.java` | Geração dos relatórios (DOCX, PDF, Excel), histórico, PDF sob demanda, exclusão. Arquivo grande (mais de 2000 linhas) |
| `service/ia/tools/RelatorioContextoTool.java` | Ferramentas que o agente chama pra pedir relatório (`solicitarGeracaoRelatorio`, `solicitarRelatorioPessoa`) e todos os parâmetros de personalização |
| `dto/relatorio/*` | Estrutura do relatório: `RelatorioEstruturadoDTO`, `RelatorioPessoaDTO`, `AcaoAnalisadaDTO`, `TarefaResponsavelDTO` etc. |

---

## 3. Onde mexer na aparência do documento gerado (DOCX, PDF, Excel)

Tudo em `RelatorioService.java` (os números de linha são aproximados; busque pelo nome do método). Não é frontend:
é Java (Apache POI para DOCX e Excel, PDFBox para PDF).

| O que mudar | Onde |
|---|---|
| **DOCX do relatório de departamento** | `gerarDocx` e os helpers `docx*` logo abaixo: `docxCapa`, `docxNumerosGrandes`, `docxTabelaAcoes`, `docxItemAcaoAnalisadaDTO` (card de cada ação), `docxSecaoMetodologia` etc. |
| **Cores do DOCX** | Constantes `COR_*` (início em `COR_DESTAQUE`, e o bloco `COR_ATENCAO`, `COR_POSITIVO`, `COR_CARD_FUNDO*`...) |
| **PDF do relatório de departamento** | `gerarPdf` e a classe interna `EscritorPdf` (o PDF é desenhado na mão, por coordenada x/y, sem "células"; larguras de coluna e quebra de linha ficam lá) |
| **Excel** | `gerarExcel` (abas Resumo, Pontos de Atenção, Destaques, Lista Completa, Acompanhamento) |
| **Relatório sobre uma pessoa** | `gerarDocxPessoa`, `gerarPdfPessoa`, `gerarExcelPessoa` (e `docxCapaPessoa`, `docxTabelaTarefasPessoa`) |
| **Quais dados e seções entram** | `processarRelatorioAsync` (monta o `RelatorioEstruturadoDTO` que os três geradores consomem) e `dto/relatorio/*` |

Pontos de atenção:
- O **mesmo conteúdo é desenhado três vezes** (DOCX, PDF, Excel). Mudou uma tabela num formato, confira os outros dois.
- O PDF não tem como "ver o resultado" só rodando o teste. Gere um arquivo de verdade e abra para conferir o visual.
- Os textos de IA (resumo, leitura do cenário, justificativas) vêm de `AgenteRelatorio.java`.
