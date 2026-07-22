import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UsuarioResumo {
  id: number;
  nome: string;
  email: string;
}

export interface AdminSistema {
  usuarioId: number;
  nome: string | null;
  email: string | null;
}

export interface GerenteDepartamento {
  id: number;
  usuarioId: number;
  usuarioNome: string | null;
  usuarioEmail: string | null;
  departamento: string;
  tipoUnidade: 'UA' | 'UG';
  createdAt: string;
}

export interface ConfiguracaoNotificacao {
  id: number;
  limiteBaixoPct: number;
  limiteBomPct: number;
}

export interface ConfiguracaoUft {
  id: number;
  tipoApi: string;
  url: string;
  tokenConfigurado: boolean;
  ativo: boolean;
  ultimaExecucao?: string;
  ultimoStatus?: string;
  ultimaMensagem?: string;
}

export interface ConfiguracaoUftRequest {
  url: string;
  token?: string;
  ativo: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private apiURL = '/api/admin';

  constructor(private http: HttpClient) {}

  souAdmin(): Observable<{ admin: boolean }> {
    return this.http.get<{ admin: boolean }>(`${this.apiURL}/sou-admin`);
  }

  listarUsuarios(): Observable<UsuarioResumo[]> {
    return this.http.get<UsuarioResumo[]>(`${this.apiURL}/usuarios`);
  }

  listarAdmins(): Observable<AdminSistema[]> {
    return this.http.get<AdminSistema[]>(`${this.apiURL}/admins`);
  }

  adicionarAdmin(usuarioId: number): Observable<void> {
    return this.http.post<void>(`${this.apiURL}/admins`, { usuarioId });
  }

  removerAdmin(usuarioId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiURL}/admins/${usuarioId}`);
  }

  listarGerentes(): Observable<GerenteDepartamento[]> {
    return this.http.get<GerenteDepartamento[]>(`${this.apiURL}/gerentes`);
  }

  adicionarGerente(usuarioId: number, departamento: string, tipoUnidade: 'UA' | 'UG'): Observable<GerenteDepartamento> {
    return this.http.post<GerenteDepartamento>(`${this.apiURL}/gerentes`, {
      usuarioId,
      departamento,
      tipoUnidade
    });
  }

  removerGerente(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiURL}/gerentes/${id}`);
  }

  buscarConfiguracoesNotificacao(): Observable<ConfiguracaoNotificacao> {
    return this.http.get<ConfiguracaoNotificacao>(`${this.apiURL}/configuracoes-notificacao`);
  }

  salvarConfiguracoesNotificacao(limiteBaixoPct: number, limiteBomPct: number): Observable<ConfiguracaoNotificacao> {
    return this.http.put<ConfiguracaoNotificacao>(`${this.apiURL}/configuracoes-notificacao`, {
      limiteBaixoPct,
      limiteBomPct
    });
  }

  listarConfiguracoesUft(): Observable<ConfiguracaoUft[]> {
    return this.http.get<ConfiguracaoUft[]>(`${this.apiURL}/integracao-uft`);
  }

  salvarConfiguracaoUft(tipoApi: string, request: ConfiguracaoUftRequest): Observable<void> {
    return this.http.put<void>(`${this.apiURL}/integracao-uft/${tipoApi}`, request);
  }
}
