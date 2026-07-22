import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService, ConfiguracaoUft, ConfiguracaoUftRequest } from '../services/admin.service';

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
}