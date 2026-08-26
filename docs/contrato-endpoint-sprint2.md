# 📄 Contrato de Endpoint — Sprint 2: Ranking Geral de Desempenho das UGs (Leaderboard)

**Destinatário**: Dallyla  
**Projeto**: BICentral / proIAp  
**Módulo**: Backend Sprint 2 (API de Ranking de UGs)

---

## 📌 Visão Geral

Este documento define o contrato da API REST que o backend disponibilizará no **Sprint 2** para alimentar o **Painel Geral Futurista de UGs (Leaderboard de Desempenho das Unidades Gestoras/Departamentos)** na tela inicial do Agente `proIAp`.

---

## 🚀 Endpoint Proposto

* **Método**: `GET`
* **URL**: `/api/proiap/ranking-ugs`
* **Autenticação**: Requerida (Bearer Token JWT)

---

## 📥 Parâmetros da Requisição (Query Params)

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
| :--- | :--- | :--- | :--- | :--- |
| `equipeId` | `Long` | Não | ID da equipe/workspace selecionada (para filtrar UGs da equipe). | `1` |
| `limite` | `Integer` | Não | Número máximo de UGs no ranking (padrão: `10`). | `10` |

---

## 📤 Resposta JSON (`200 OK`)

```json
{
  "dataExtracao": "2026-08-13T10:00:00Z",
  "totalUgs": 7,
  "ranking": [
    {
      "id": "PROEST",
      "sigla": "PROEST",
      "nome": "Pró-Reitoria de Assistência Estudantil",
      "posicao": 1,
      "posicaoAnterior": 3,
      "percentual": 88.5,
      "percentualAnterior": 72.0,
      "totalTarefas": 42,
      "tarefasConcluidas": 37,
      "variacaoPosicao": 2,
      "tendencia": "SUBIU"
    },
    {
      "id": "PROAD",
      "sigla": "PROAD",
      "nome": "Pró-Reitoria de Administração e Finanças",
      "posicao": 2,
      "posicaoAnterior": 2,
      "percentual": 79.2,
      "percentualAnterior": 79.2,
      "totalTarefas": 55,
      "tarefasConcluidas": 43,
      "variacaoPosicao": 0,
      "tendencia": "ESTAVEL"
    },
    {
      "id": "DTI",
      "sigla": "DTI",
      "nome": "Diretoria de Tecnologia da Informação",
      "posicao": 3,
      "posicaoAnterior": 1,
      "percentual": 68.4,
      "percentualAnterior": 85.0,
      "totalTarefas": 60,
      "tarefasConcluidas": 41,
      "variacaoPosicao": -2,
      "tendencia": "CAIU"
    }
  ]
}
```

---

## 💻 Interfaces TypeScript no Frontend (`agent.service.ts`)

```typescript
export interface UgRankingItemDTO {
  id: string;
  sigla: string;
  nome: string;
  posicao: number;
  posicaoAnterior: number;
  percentual: number;
  percentualAnterior: number;
  totalTarefas: number;
  tarefasConcluidas: number;
  variacaoPosicao: number; // Ex: +2 (subiu 2 posições), -1 (caiu 1), 0 (estável)
  tendencia: 'SUBIU' | 'CAIU' | 'ESTAVEL';
}

export interface RankingUgsResponseDTO {
  dataExtracao: string;
  totalUgs: number;
  ranking: UgRankingItemDTO[];
}
```

---

## 🗄️ Exemplo de Consulta SQL Sugerida no Backend (Spring Boot)

```sql
SELECT 
    p.departamento AS sigla,
    gd.nome_completo AS nome,
    ROUND(AVG(CASE WHEN p.data_extracao = (SELECT MAX(data_extracao) FROM pat_execucao_departamento) THEN p.percentual_execucao END) * 100, 1) AS percentual,
    ROUND(AVG(CASE WHEN p.data_extracao = (SELECT DISTINCT data_extracao FROM pat_execucao_departamento ORDER BY data_extracao DESC LIMIT 1 OFFSET 1) THEN p.percentual_execucao END) * 100, 1) AS percentual_anterior
FROM pat_execucao_departamento p
LEFT JOIN gerentes_departamento gd ON gd.departamento = p.departamento
GROUP BY p.departamento, gd.nome_completo
ORDER BY percentual DESC NULLS LAST;
```
