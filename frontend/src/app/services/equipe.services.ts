import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Equipe{
  id?: number;
  nome: string;
  descricao: string;
  role?: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

export interface MembroEquipe {
  usuarioId: number;
  nomeExibicao: string;
  email: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

@Injectable({
  providedIn: 'root'
})
export class EquipeService{
  private apiURL = '/api/equipes';

  constructor(private http: HttpClient){ }

  criar(equipe: Equipe): Observable<Equipe>{
    return this.http.post<Equipe>(this.apiURL, equipe);
  }

  remover(id: number | string): Observable<void>{
    return this.http.delete<void>(`${this.apiURL}/${id}`);
  }

  listarMinhasEquipes(): Observable<Equipe[]>{
    return this.http.get<Equipe[]>(this.apiURL)
  }
  atualizar(id: number, equipe: Equipe): Observable<Equipe>{
    return this.http.put<Equipe>(`${this.apiURL}/${id}`, equipe);
  }

  listarMembros(equipeId: number): Observable<MembroEquipe[]> {
    return this.http.get<MembroEquipe[]>(`${this.apiURL}/${equipeId}/membros`);
  }

  adicionarMembro(equipeId: number, email: string, role: string): Observable<MembroEquipe> {
    return this.http.post<MembroEquipe>(`${this.apiURL}/${equipeId}/membros`, { email, role });
  }

  removerMembro(equipeId: number, usuarioId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiURL}/${equipeId}/membros/${usuarioId}`);
  }

  alterarPapel(equipeId: number, usuarioId: number, role: string): Observable<MembroEquipe> {
    return this.http.put<MembroEquipe>(`${this.apiURL}/${equipeId}/membros/${usuarioId}`, { role });
  }
}
