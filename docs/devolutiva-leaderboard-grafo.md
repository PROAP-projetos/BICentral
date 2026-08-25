# Devolutiva técnica — Leaderboard & Grafo de Atividades

**Pra**: Lean
**De**: Dallyla
**Contexto**: revisei a PR inteira (`feature/leaderboard-animacao-ug`) e essa é a devolutiva do que encontrei sobre dado real, backend e o contrato do endpoint — pra te dar tudo que precisa antes de você fazer a integração real do Sprint 2.

---

## 1. Como o painel é acessado hoje

Tirei o botão "Home" separado da sidebar do agente — ele duplicava o que o logo já faz (voltar pro `/`), sem ganho nenhum. No lugar ficou um link discreto **"Painel de ranking"** na sidebar.

Como abre: não é uma rota nova nem uma página separada — clicar no link troca a área do chat pelo painel, na mesma tela do agente (o cabeçalho e a sidebar continuam ali). Tem um botão **"Voltar para o chat"** no topo do painel pra voltar. É basicamente um "modo" alternativo dentro do mesmo componente do agente, não uma navegação de verdade.

## 2. O contrato de endpoint está desatualizado

O `docs/contrato-endpoint-sprint2.md` (que você escreveu) descreve `/api/proiap/ranking-ugs` com um formato específico (`dataExtracao`, `totalUgs`, `ranking: [...]`, campos como `totalTarefas`/`tarefasConcluidas`/`tendencia`). O endpoint que existe de verdade é outro:

**`GET /api/ranking`** (não `/api/proiap/ranking-ugs`)

Requer login (JWT no header `Authorization`). Aceita `?tipoUnidade=UG` ou `?tipoUnidade=UA` opcional.

Resposta — array direto, sem wrapper:
```json
[
  {
    "departamento": "Pró-Reitoria de Avaliação e Planejamento - PROAP",
    "tipoUnidade": "UG",
    "mediaExecucaoPct": 25.16,
    "qtdAcoes": 71,
    "posicaoAtual": 4,
    "posicaoAnterior": 5
  }
]
```

| Campo | Descrição |
|---|---|
| `departamento` | nome completo, cru, como está no banco |
| `tipoUnidade` | `UG`, `UA`, ou `null` se não classificado no painel admin |
| `mediaExecucaoPct` | % de execução do PAT (ano corrente) |
| `qtdAcoes` | quantidade de ações distintas consideradas |
| `posicaoAtual` | posição no ranking de hoje |
| `posicaoAnterior` | posição no snapshot mais recente anterior, ou `null` se não existe ainda |

Não tem `sigla`, `nome` separado, `totalTarefas`, `tarefasConcluidas` nem `tendencia` prontos — isso precisa ser calculado no frontend (ver seção 4). `posicaoAnterior` vem de uma tabela de snapshot diário (`ranking_pat_snapshots`, alimentada pelo `RankingSnapshotJob`), não de uma coluna `data_extracao` na própria tabela.

Consumo em Angular (`ranking.service.ts` já existe no projeto, pronto pra usar):
```typescript
listarRanking(tipoUnidade?: 'UA' | 'UG'): Observable<RankingDepartamento[]> {
  const url = tipoUnidade
    ? `http://localhost:8080/api/ranking?tipoUnidade=${tipoUnidade}`
    : 'http://localhost:8080/api/ranking';
  return this.http.get<RankingDepartamento[]>(url);
}
```

## 3. O dado real é bem mais bagunçado do que 7 UGs fixas

O contrato original assume um punhado de UGs limpas (PROEST, PROAD, DTI...). A realidade: **105 departamentos** na tabela, incluindo 77 coordenações de curso, 5 campus, 15 UGs de verdade e 8 outros (setores vinculados a gabinete, etc). Os nomes têm formatos bem inconsistentes, por exemplo:

- `"Campus Universitário de Arraias - CUAR"` → aqui o que vem depois do traço É a sigla
- `"Coordenação do Curso de Pedagogia - Arraias"` → aqui o que vem depois do traço é o CAMPUS, não uma sigla
- `"Setor de Integridade e Transparência - SITAI - Chefia de Gabinete - GAB"` → aqui a sigla de verdade fica no MEIO (SITAI), e "GAB" no final é só vínculo hierárquico, não identifica a unidade
- Alguns têm espaçamento irregular no traço (ex: `"...Saúde- CESAU - CUP"`, sem espaço antes do primeiro traço)

Se o frontend tratar tudo como "nome - SIGLA" simples (pegando sempre o que vem depois do último traço), vai errar a sigla mostrada em boa parte dos casos.

## 4. Lógica de classificação — referência pronta

Estudei os 105 nomes reais e cheguei nessa lógica (testada contra a lista inteira). Fica de referência pra você adaptar no componente, se ajudar:

```typescript
private static readonly PREFIXOS_COORDENACAO: RegExp[] = [
  /^Coordenação do Curso de\s+/i,
  /^Coordenação do Programa de Pós-graduação em\s+/i,
  /^Coordenação da\s+/i,
  /^Coordenação de\s+/i,
  /^Coord\.?\s+/i, // cobre "COORD POS-GRAD..." e "COORD. CURSO..." (grafia abreviada)
];

private static readonly SUFIXOS_VINCULO_CONHECIDOS = new Set(['GAB']);

// separador tolerante a espaçamento inconsistente (ex: "Saúde- CESAU" sem espaço antes do traço)
private static readonly SEPARADOR_SEGMENTO = /\s+-\s*|\s*-\s+/;

private dividirSegmentos(texto: string): string[] {
  return texto.split(LeaderboardUgComponent.SEPARADOR_SEGMENTO).map(p => p.trim()).filter(p => p.length > 0);
}

private classificarDepartamento(nomeCompleto: string, tipoUnidade: 'UA' | 'UG' | null) {
  if (/^Coord/i.test(nomeCompleto)) {
    let semPrefixo = nomeCompleto;
    for (const prefixo of LeaderboardUgComponent.PREFIXOS_COORDENACAO) {
      if (prefixo.test(nomeCompleto)) {
        semPrefixo = nomeCompleto.replace(prefixo, '');
        break;
      }
    }
    const partes = this.dividirSegmentos(semPrefixo);
    const curso = partes[0];
    const vinculo = partes.length > 1 ? partes[partes.length - 1] : '';
    return { sigla: curso, nome: vinculo ? `Coordenação — ${vinculo}` : 'Coordenação de curso', categoria: 'coordenacao' };
  }

  const partes = this.dividirSegmentos(nomeCompleto);

  if (/^Campus/i.test(nomeCompleto) && partes.length > 1) {
    return { sigla: partes[partes.length - 1], nome: partes[0], categoria: 'campus' };
  }

  if (partes.length > 1) {
    const ultimo = partes[partes.length - 1];
    const categoria = tipoUnidade === 'UG' ? 'ug' : 'outro';

    if (partes.length >= 3) return { sigla: partes[1], nome: partes[0], categoria }; // sigla no meio, último é só vínculo
    if (LeaderboardUgComponent.SUFIXOS_VINCULO_CONHECIDOS.has(ultimo.toUpperCase())) return { sigla: partes[0], nome: partes[0], categoria };
    return { sigla: ultimo, nome: partes[0], categoria };
  }

  return { sigla: nomeCompleto, nome: nomeCompleto, categoria: tipoUnidade === 'UG' ? 'ug' : 'outro' };
}
```

`totalTarefas`/`tarefasConcluidas` não têm equivalente no `/api/ranking` — só tem `qtdAcoes` (contagem de ações, não tarefas). Se precisar do nível de tarefa, dá pra usar o `/api/tarefas?departamento=X` (criado nessa sessão, ver seção 7) que devolve tarefas individuais por departamento.

## 5. Sugestão: filtro por categoria e por campus

Com 105 departamentos numa lista só, fica difícil de navegar. Sugestão de filtro, usando a `categoria` calculada na seção 4:

- Pills/abas: **Todos / Campus / Coordenação / UG / Unidade Acadêmica** — filtra a lista pela categoria.
- Um segundo filtro (dropdown) só de **campus** (Palmas, Gurupi, Arraias, Miracema, Porto Nacional) — útil principalmente pra achar coordenações de um campus específico, já que são a maioria (77 de 105).

Pra identificar o campus de cada item (não vem pronto do backend, precisa procurar no nome também):
```typescript
const CAMPI_CONHECIDOS = ['Palmas', 'Gurupi', 'Arraias', 'Miracema', 'Porto Nacional'];

function identificarCampus(nomeCompleto: string): string | null {
  return CAMPI_CONHECIDOS.find(c => nomeCompleto.includes(c)) ?? null;
}
```

## 6. Backend do `/api/ranking` — bugs corrigidos, já pode confiar

- Tinha um `NullPointerException` (erro 500) quando um departamento tinha todas as ações com `%` nulo — corrigido com `COALESCE(AVG(percentual_execucao), 0)`.
- **Contagem de ações precisa ser `DISTINCT`, sempre.** A tabela `pat_execucao_departamento` tem mais de uma linha pra mesma ação dentro do MESMO departamento (não é só entre departamentos diferentes) — por isso `COUNT(*)` inflava o número (chegava a mostrar ~5249 quando o real era bem menor). O fix foi trocar pra `COUNT(DISTINCT codigo_acao)`. Se for escrever qualquer query nova em cima dessa tabela (ou de `pat_acoes`/tarefas relacionadas) que precise contar ações, usa `codigo_acao` com `DISTINCT` — nunca `COUNT(*)` puro.
- Isso vale também se for somar `qtdAcoes` entre vários departamentos pra um total geral: uma ação "geral" pode se repetir em até 96 departamentos diferentes (são 536 ações distintas no total, não a soma direta de todos os `qtdAcoes` do array).

## 7. Grafo de Atividades — as relações entre departamentos não têm como ser reais

Testei construir o grafo com dado real (UGs + tarefas de cada uma, via `/api/tarefas?departamento=X`, endpoint novo que criei porque só a IA tinha acesso a isso antes). Isso funciona.

O que **não dá pra fazer**: as linhas conectando tarefas de departamentos diferentes (tipo "essa tarefa da DTI se relaciona com aquela da PROAD"). Não existe esse dado em lugar nenhum do banco — o PAT não registra dependência entre tarefas de setores diferentes, só o que é interno de cada tarefa (título, responsável, %, prazo). Pra isso ser real, ou a UFT expõe esse dado (não expõe hoje), ou vira uma feature de cadastro manual (alguém da PROAP marcando à mão que uma ação se relaciona com outra) — não é algo que dá pra inferir do dado que já sincronizamos.

Minha sugestão: sem essas relações, um grafo de nós conectados não agrega muito sobre uma lista/tabela agrupada por UG — vale considerar se o grafo ainda faz sentido como formato, ou se um layout mais simples entrega o mesmo valor com menos esforço.

## 8. Sugestão pequena: nome do botão "ECharts Bar"

O botão que alterna pra visualização em gráfico de barras mostra "ECharts Bar" pro usuário final — nome de biblioteca técnica, não faz sentido pra quem usa o painel. Sugiro trocar por algo tipo "Gráfico de Barras".
