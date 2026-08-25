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
      ? `http://localhost:8080/api/ranking?tipoUnidade=${tipoUnidade}`
      : 'http://localhost:8080/api/ranking';
    return this.http.get<RankingDepartamento[]>(url);
  }

  buscarResumo(): Observable<{ totalAcoesUnicas: number }> {
    return this.http.get<{ totalAcoesUnicas: number }>('http://localhost:8080/api/ranking/resumo');
  }
}
