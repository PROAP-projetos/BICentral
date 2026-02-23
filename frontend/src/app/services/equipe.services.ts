import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Equipe{
  id?: number;
  nome: string;
  descricao: string;
  role?: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

@Injectable({
  providedIn: 'root'
})
export class EquipeService{
  private apiURL = 'http://localhost:8080/api/equipes';

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




}
