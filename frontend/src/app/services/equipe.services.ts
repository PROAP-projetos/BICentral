import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Equipe{
  id?: number;
  nome: string;
  descricao: string;
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

  listarMinhasEquipes(): Observable<Equipe[]>{
    return this.http.get<Equipe[]>(this.apiURL)
  }
}
