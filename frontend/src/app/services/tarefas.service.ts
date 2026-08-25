import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TarefaGrafo {
  titulo: string;
  responsavel: string | null;
  percentual: number | null;
  status: 'concluida' | 'em_andamento' | 'atrasada';
}

@Injectable({ providedIn: 'root' })
export class TarefasService {
  constructor(private http: HttpClient) {}

  listarPorDepartamento(departamento: string): Observable<TarefaGrafo[]> {
    const url = `http://localhost:8080/api/tarefas?departamento=${encodeURIComponent(departamento)}`;
    return this.http.get<TarefaGrafo[]>(url);
  }
}
