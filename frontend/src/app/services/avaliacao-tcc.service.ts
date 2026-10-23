import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TempoManual = '<5' | '5-15' | '15-60' | '>60' | 'pedir';

export interface ItemAvaliacao {
  id: string;
  tipo: number;
  ordem: number;
  pergunta: string;
  referencia: string;
  resposta: string;
  correta: boolean | null;
  completa: boolean | null;
  tempoManual: TempoManual | null;
  comentario: string | null;
}

export interface ItensAvaliacaoResponse {
  itens: ItemAvaliacao[];
  souAdmin: boolean;
}

export interface NovoItemAvaliacao {
  id: string;
  tipo: number;
  ordem: number;
  pergunta: string;
  referencia: string;
  resposta: string;
}

@Injectable({ providedIn: 'root' })
export class AvaliacaoTccService {
  private readonly base = '/api/avaliacao-tcc';

  constructor(private http: HttpClient) {}

  listar(): Observable<ItensAvaliacaoResponse> {
    return this.http.get<ItensAvaliacaoResponse>(`${this.base}/itens`);
  }

  salvar(itemId: string, avaliacao: { correta: boolean | null; completa: boolean | null; tempoManual: TempoManual | null; comentario: string | null }): Observable<void> {
    return this.http.post<void>(`${this.base}/itens/${encodeURIComponent(itemId)}/resposta`, avaliacao);
  }

  importar(itens: NovoItemAvaliacao[]): Observable<{ importados: number }> {
    return this.http.post<{ importados: number }>(`${this.base}/itens`, itens);
  }

  resultado(): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.base}/resultado`);
  }
}
