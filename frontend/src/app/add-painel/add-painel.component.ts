// Local: frontend/src/app/add-painel/add-painel.component.ts

import { Component } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http'; // Importamos o módulo aqui
import { FormsModule } from '@angular/forms'; // Importamos o FormsModule aqui
import { CommonModule } from '@angular/common';

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
  // ... (O RESTO DO CÓDIGO DA CLASSE É O MESMO) ...

  painel: AddPainel = {
    nome: '',
    linkPowerBi: ''
  };

  mensagem: string = '';
  erro: boolean = false;

  private API_URL = 'http://localhost:8080/api/painel';

  constructor(private http: HttpClient) { }

  salvarPainel() {
    this.mensagem = '';
    this.erro = false;

    this.http.post<AddPainel>(this.API_URL, this.painel).subscribe({
      next: (resposta) => {
        this.mensagem = `Painel "${resposta.nome}" salvo com sucesso! ID: ${resposta.id}`;
        this.painel = { nome: '', linkPowerBi: '' };
      },
      error: (e) => {
        this.erro = true;
        console.error("Erro ao salvar painel:", e);
        this.mensagem = 'Erro ao conectar ou salvar. Verifique o backend (porta 8080) e as credenciais.';
      }
    });
  }
}
