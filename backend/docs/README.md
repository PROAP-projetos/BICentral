# BICentral — Backend

Backend do BICentral (Spring Boot), incluindo o agente de IA proIAp.

## API — Ranking geral do PAT

Endpoint que devolve o ranking de execução do PAT por departamento, em JSON puro (sem passar pelo agente de IA). Usado pelo painel de ranking do frontend.

### `GET /api/ranking`

Requer login (mesmo token JWT usado no resto do app — vai no header `Authorization: Bearer <token>`).

**Parâmetros de query (opcionais):**

| Parâmetro | Tipo | Descrição |
|---|---|---|
| `tipoUnidade` | string | Filtra por `UG` (Unidade Gestora) ou `UA` (Unidade Acadêmica). Se omitido, traz todos os departamentos. |

**Exemplos:**
```
GET /api/ranking
GET /api/ranking?tipoUnidade=UG
```

**Resposta — `200 OK`:** lista de departamentos, já ordenada do melhor pro pior (`mediaExecucaoPct` decrescente).

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

| Campo | Tipo | Descrição |
|---|---|---|
| `departamento` | string | Nome do departamento |
| `tipoUnidade` | string ou `null` | `UG`, `UA`, ou `null` se o departamento ainda não foi classificado no painel admin (tela "Gerentes por Departamento") |
| `mediaExecucaoPct` | number | Média de execução do PAT (ano corrente), em % |
| `qtdAcoes` | number | Quantidade de ações consideradas no cálculo |
| `posicaoAtual` | number | Posição no ranking de hoje (1 = melhor) |
| `posicaoAnterior` | number ou `null` | Posição no snapshot anterior mais recente. `null` quando ainda não existe snapshot de um dia anterior (ex: departamento novo, ou é o primeiro dia de uso) — nesse caso, não tem "antes" pra comparar |

### Como consumir no Angular

```typescript
listarRanking(tipoUnidade?: string): Observable<RankingDepartamento[]> {
  const url = tipoUnidade
    ? `http://localhost:8080/api/ranking?tipoUnidade=${tipoUnidade}`
    : 'http://localhost:8080/api/ranking';
  return this.http.get<RankingDepartamento[]>(url);
}
```

O `auth.interceptor.ts` já anexa o token automaticamente — não precisa fazer nada a mais de autenticação.

### Como os dados são calculados

- **Hoje**: consulta ao vivo em `pat_execucao_departamento`, sem cache.
- **Ontem** (`posicaoAnterior`): comparado contra o snapshot mais recente salvo em `ranking_pat_snapshots`, uma tabela alimentada 1x por dia pelo job `RankingSnapshotJob`. Não é necessariamente "ontem" no calendário — é o último snapshot que existe antes de hoje (o job pode não ter rodado todo santo dia).
- **Classificação UG/UA**: vem da tabela `gerentes_departamento`, gerenciada manualmente na tela "Gerentes por Departamento" do painel admin (`/admin`). Um departamento sem cadastro ali aparece com `tipoUnidade: null`.
