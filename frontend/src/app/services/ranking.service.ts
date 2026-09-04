import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RankingDepartamento {
  departamento: string;
  tipoUnidade: 'UA' | 'UG' | null;
  mediaExecucaoPct: number;
  qtdAcoes: number;
  posicaoAtual: number;
  posicaoAnterior: number | null;
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
}
