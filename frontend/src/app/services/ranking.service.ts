import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RankingDepartamento {
  departamento: string;
  tipoUnidade: 'UA' | 'UG' | null;
  mediaExecucaoPct: number;
  qtdAcoes: number;
  qtdAcoesConcluidas: number;
  posicaoAtual: number;
  posicaoAnterior: number | null;
}

export interface CompartilhamentoDepartamentos {
  origem: string;
  destino: string;
  qtdAcoes: number;
  diferencaMediaPct: number;
}

export interface RankingAtualizacao {
  atualizadoEm: string | null;
}

export interface RankingResumo {
  totalAcoes: number;
  acoesConcluidas: number;
}

@Injectable({ providedIn: 'root' })
export class RankingService {
  constructor(private http: HttpClient) {}

  listarRanking(tipoUnidade?: 'UA' | 'UG'): Observable<RankingDepartamento[]> {
    const url = tipoUnidade
      ? `/api/ranking?tipoUnidade=${tipoUnidade}`
      : '/api/ranking';
    return this.http.get<RankingDepartamento[]>(url);
  }

  resumo(tipoUnidade?: 'UA' | 'UG'): Observable<RankingResumo> {
    const url = tipoUnidade
      ? `/api/ranking/resumo?tipoUnidade=${tipoUnidade}`
      : '/api/ranking/resumo';
    return this.http.get<RankingResumo>(url);
  }

  atualizacao(): Observable<RankingAtualizacao> {
    return this.http.get<RankingAtualizacao>('/api/ranking/atualizacao');
  }

  compartilhamento(tipoUnidade?: 'UA' | 'UG'): Observable<CompartilhamentoDepartamentos[]> {
    const url = tipoUnidade
      ? `/api/ranking/compartilhamento?tipoUnidade=${tipoUnidade}`
      : '/api/ranking/compartilhamento';
    return this.http.get<CompartilhamentoDepartamentos[]>(url);
  }
}
