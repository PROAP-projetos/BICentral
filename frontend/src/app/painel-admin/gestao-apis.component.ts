import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService, ConfiguracaoUft, ConfiguracaoUftRequest, ResultadoTesteUft } from '../services/admin.service';

@Component({
  selector: 'app-gestao-apis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-apis.component.html',
  styleUrls: ['./gestao-apis.component.css']
})
export class GestaoApisComponent implements OnInit {
  @Output() alterado = new EventEmitter<string>();

  apis: ConfiguracaoUft[] = [];
  tokensAEditar: { [tipoApi: string]: string } = {};
  carregando = true;
  salvando = false;
  testando: { [tipoApi: string]: boolean } = {};
  resultadoTeste: { [tipoApi: string]: ResultadoTesteUft | null } = {};

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.adminService.listarConfiguracoesUft().subscribe({
      next: (dados) => {
        this.apis = dados;
        this.tokensAEditar = {};
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.alterado.emit('Erro ao carregar as integrações UFT.');
      }
    });
  }

  salvar(api: ConfiguracaoUft): void {
    if (this.salvando || !api.url) return;

    this.salvando = true;
    const request: ConfiguracaoUftRequest = {
      url: api.url,
      token: this.tokensAEditar[api.tipoApi],
      ativo: api.ativo
    };

    this.adminService.salvarConfiguracaoUft(api.tipoApi, request).subscribe({
      next: () => {
        this.salvando = false;
        this.alterado.emit(`Configuração da API ${api.tipoApi} salva com sucesso.`);
        this.carregar();
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit(`Erro ao salvar a API ${api.tipoApi}.`);
      }
    });
  }

  // O backend grava ultima_execucao em UTC sem marcar o fuso no JSON — sem isso, o pipe
  // "date" do Angular mostra a hora crua como se já fosse local (3h adiantado no horário de
  // Brasília). Mesmo helper já usado em agent.component.ts pro histórico de relatórios.
  paraDataUtc(valor: string | null | undefined): Date | null {
    if (!valor) return null;
    const temFuso = /(Z|[+-]\d{2}:?\d{2})$/.test(valor);
    const data = new Date(temFuso ? valor : valor + 'Z');
    return isNaN(data.getTime()) ? null : data;
  }

  testar(api: ConfiguracaoUft): void {
    if (this.testando[api.tipoApi] || !api.url) return;

    this.testando[api.tipoApi] = true;
    this.resultadoTeste[api.tipoApi] = null;
    const request: ConfiguracaoUftRequest = {
      url: api.url,
      token: this.tokensAEditar[api.tipoApi],
      ativo: api.ativo
    };

    this.adminService.testarConexaoUft(api.tipoApi, request).subscribe({
      next: (resultado) => {
        this.testando[api.tipoApi] = false;
        this.resultadoTeste[api.tipoApi] = resultado;
      },
      error: () => {
        this.testando[api.tipoApi] = false;
        this.resultadoTeste[api.tipoApi] = { sucesso: false, mensagem: 'Erro ao testar a conexão.' };
      }
    });
  }
}