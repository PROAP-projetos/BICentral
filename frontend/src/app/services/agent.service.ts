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

export interface TarefasAtrasadasResumo {
  quantidade: number;
  tituloMaisUrgente: string | null;
  diasAtraso: number | null;
}

export interface RelatorioHistoricoItem {
  id: number;
  departamento: string;
  tipo: string;
  status: 'PROCESSANDO' | 'PRONTO' | 'ERRO';
  arquivo_url: string | null;
  pdf_url: string | null;
  mensagem_erro: string | null;
  criado_em: string;
  concluido_em: string | null;
}

export interface RelatorioPdfResponse {
  pdf_url: string;
}

export interface TarefaCritica {
  titulo: string;
  responsavel: string;
  prazo: string;
}

export interface Notificacao {
  emoji: string;
  departamento: string;
  mensagem: string;
  percentual: number;
  tarefas: TarefaCritica[];
}

export interface PainelAtrasos {
  departamento: string;
  grafico: any;
  tarefas: TarefaCritica[];
}

export interface PainelIa {
  id: number;
  titulo: string;
  especificacao: any;
  criadoEm: string;
}

export interface UsoIa {
  gastoTotal: number;
  limite: number;
  souTester: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AgentService {
  private readonly apiUrl = '/api/ia';

  constructor(private http: HttpClient) { }

  consultar(texto: string, equipeId: number, modelo: string, sessaoId: string): Observable<any> {
    const urlNova = '/api/proiap/perguntar';

    const body = {
      texto: texto,
      equipeId: equipeId,
      modelo: modelo,
      sessaoId: sessaoId 
    };

    return this.http.post(urlNova, body, {
      withCredentials: true
    });
  }

  listarFontes(equipeId: number): Observable<FontesAgenteResponse> {
    const params = new HttpParams().set('equipeId', equipeId);
    return this.http.get<FontesAgenteResponse>(`${this.apiUrl}/fontes`, { params });
  }

  listarNotificacoes(): Observable<Notificacao[]> {
    const urlNotificacoes = '/api/proiap/notificacoes';
    return this.http.get<Notificacao[]>(urlNotificacoes, { withCredentials: true });
  }

  buscarMinhasTarefasAtrasadas(): Observable<TarefasAtrasadasResumo> {
    const url = '/api/tarefas/minhas-atrasadas';
    return this.http.get<TarefasAtrasadasResumo>(url, { withCredentials: true });
  }

  salvarPainelIa(titulo: string, especificacao: any): Observable<PainelIa> {
    const url = '/api/paineis-ia';
    return this.http.post<PainelIa>(url, { titulo, especificacao }, { withCredentials: true });
  }

  listarPaineisIa(): Observable<PainelIa[]> {
    const url = '/api/paineis-ia';
    return this.http.get<PainelIa[]>(url, { withCredentials: true });
  }

  consultarUsoIa(): Observable<UsoIa> {
    const url = '/api/uso-ia';
    return this.http.get<UsoIa>(url, { withCredentials: true });
  }

  enviarFeedbackInteracao(interacaoId: number, comentario: string): Observable<void> {
    const url = `/api/uso-ia/interacoes/${interacaoId}/feedback`;
    return this.http.post<void>(url, { comentario }, { withCredentials: true });
  }

  excluirPainelIa(id: number): Observable<void> {
    const url = `/api/paineis-ia/${id}`;
    return this.http.delete<void>(url, { withCredentials: true });
  }

  buscarPainelAtrasos(departamento: string): Observable<PainelAtrasos> {
    const url = '/api/proiap/painel-atrasos';
    const params = new HttpParams().set('departamento', departamento);
    return this.http.get<PainelAtrasos>(url, { params, withCredentials: true });
  }

  gerarRelatorio(departamento: string, tipo: string): Observable<RelatorioSolicitadoResponse> {
    const url = '/api/proiap/relatorio/gerar';
    return this.http.post<RelatorioSolicitadoResponse>(url, { departamento, tipo }, { withCredentials: true });
  }

  statusRelatorio(id: number): Observable<RelatorioStatusResponse> {
    const url = `/api/proiap/relatorio/status/${id}`;
    return this.http.get<RelatorioStatusResponse>(url, { withCredentials: true });
  }

  listarDepartamentosRelatorio(): Observable<string[]> {
    const url = '/api/proiap/relatorio/departamentos';
    return this.http.get<string[]>(url, { withCredentials: true });
  }

  listarMeusRelatorios(): Observable<RelatorioHistoricoItem[]> {
    const url = '/api/proiap/relatorio/meus';
    return this.http.get<RelatorioHistoricoItem[]>(url, { withCredentials: true });
  }

  gerarPdfRelatorio(id: number): Observable<RelatorioPdfResponse> {
    const url = `/api/proiap/relatorio/${id}/pdf`;
    return this.http.post<RelatorioPdfResponse>(url, {}, { withCredentials: true });
  }

  excluirRelatorio(id: number): Observable<void> {
    const url = `/api/proiap/relatorio/${id}`;
    return this.http.delete<void>(url, { withCredentials: true });
  }
}