// Local: frontend/src/app/add-painel/add-painel.component.ts

import { Component } from '@angular/core';
import { HttpClient, HttpClientModule, HttpErrorResponse } from '@angular/common/http'; // Adicionado HttpErrorResponse
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
// Interface simples para garantir a tipagem dos dados enviados
interface AddPainel {
  id?: number;
  nome: string;
  linkPowerBi: string;
}

@Component({
  selector: 'app-add-painel',
  standalone: true, // <-- INDICA QUE É UM COMPONENTE AUTÔNOMO
  imports: [
    FormsModule,       // <-- Adicione FormsModule para usar [(ngModel)]
    HttpClientModule,   // <-- Adicione HttpClientModule para usar o HttpClient
    CommonModule
  ],
  templateUrl: './add-painel.component.html',
  styleUrls: ['./add-painel.component.css']
})
export class AddPainelComponent {

  painel: AddPainel = {
    nome: '',
    linkPowerBi: ''
  };

  mensagem: string = '';
  erro: boolean = false;

  private API_URL = 'http://localhost:8080/api/painel';

  constructor(private http: HttpClient, private router: Router) { }

  salvarPainel() {
    this.mensagem = '';
    this.erro = false;

    this.http.post<AddPainel>(this.API_URL, this.painel).subscribe({
      next: (resposta) => {
        // Sucesso: Painel salvo
        this.mensagem = `Painel "${resposta.nome}" salvo com sucesso! ID: ${resposta.id}`;
        this.painel = { nome: '', linkPowerBi: '' };
        this.router.navigate(['/']); // Redireciona para a página inicial
      },
      error: (e: HttpErrorResponse) => { // Tipando o erro para HttpErrorResponse
        this.erro = true;
        console.error("Erro ao salvar painel:", e);

        // 1. Verifica se o status HTTP é 409 (Conflict)
        if (e.status === 409) {
          // A mensagem de erro esperada do backend é "Painel já cadastrado"
          // O corpo da resposta de erro (e.error) contém a string que enviamos do Controller.
          this.mensagem = e.error || 'Painel já cadastrado (conflito de link)';
        } else if (e.status === 0) {
          // Status 0 é geralmente ERR_CONNECTION_REFUSED (backend desligado/inacessível)
          this.mensagem = 'Erro de conexão: O backend (porta 8080) está desligado ou inacessível.';
        }
        else {
          // Qualquer outro erro HTTP
          this.mensagem = `Erro ao salvar. Status: ${e.status}. Verifique o console.`;
        }
      }
    });
  }
}
