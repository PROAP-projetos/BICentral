import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IngestaoResponse } from '../models/ingestao.model';

@Injectable({
  providedIn: 'root',
})
export class IngestaoIaService {
  private readonly apiUrl = '/api/ia/ingestao';

  constructor(private http: HttpClient) {}

  enviarArquivo(
    arquivo: File,
    equipe: string,
    visibilidade: 'PUBLICO' | 'PRIVADO'
  ): Observable<IngestaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    formData.append('equipe', equipe);
    formData.append('visibilidade', visibilidade);

    return this.http.post<IngestaoResponse>(this.apiUrl, formData);
  }
}
