import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FonteAgente {
  nome: string;
  acesso: 'publico' | 'privado';
}

export interface ModeloAgente {
  nome: string;
  detalhes: string;
}

export interface FontesAgenteResponse {
  fontes: FonteAgente[];
  modelo: ModeloAgente;
  equipe: string;
}

export interface ConsultaAgenteResponse {
  pergunta: string;
  resposta: string;
  contextos: string[];
  modelo: string;
  equipe: string;
}

export interface RelatorioSolicitadoResponse {
  id: number;
  status: string;
  mensagem: string;
}

export interface RelatorioStatusResponse {
  id: number;
  status: 'PROCESSANDO' | 'PRONTO' | 'ERRO';
  arquivo_url: string | null;
  mensagem_erro: string | null;
  departamento: string;
  tipo: string;
}

@Injectable({
  providedIn: 'root'
})
export class AgentService {
  private readonly apiUrl = '/api/ia';

  constructor(private http: HttpClient) { }

  consultar(texto: string, equipeId: number, modelo: string): Observable<any> {
    const urlNova = 'http://localhost:8080/api/proiap/perguntar';

    const body = {
      texto: texto,
      equipeId: equipeId,
      modelo: modelo
    };

    return this.http.post(urlNova, body, {
      withCredentials: true
    });
  }

  listarFontes(equipeId: number): Observable<FontesAgenteResponse> {
    const params = new HttpParams().set('equipeId', equipeId);
    return this.http.get<FontesAgenteResponse>(`${this.apiUrl}/fontes`, { params });
  }

  listarNotificacoes(): Observable<string[]> {
    const urlNotificacoes = 'http://localhost:8080/api/proiap/notificacoes';
    return this.http.get<string[]>(urlNotificacoes, { withCredentials: true });
  }

  gerarRelatorio(departamento: string, tipo: string): Observable<RelatorioSolicitadoResponse> {
    const url = 'http://localhost:8080/api/proiap/relatorio/gerar';
    return this.http.post<RelatorioSolicitadoResponse>(url, { departamento, tipo }, { withCredentials: true });
  }

  statusRelatorio(id: number): Observable<RelatorioStatusResponse> {
    const url = `http://localhost:8080/api/proiap/relatorio/status/${id}`;
    return this.http.get<RelatorioStatusResponse>(url, { withCredentials: true });
  }

  listarDepartamentosRelatorio(): Observable<string[]> {
    const url = 'http://localhost:8080/api/proiap/relatorio/departamentos';
    return this.http.get<string[]>(url, { withCredentials: true });
  }
}