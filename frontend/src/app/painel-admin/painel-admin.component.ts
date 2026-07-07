import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  AdminService,
  AdminSistema,
  GerenteDepartamento,
  UsuarioResumo
} from '../services/admin.service';
import { GestaoAdminsComponent } from './gestao-admins.component';
import { GestaoGerentesComponent } from './gestao-gerentes.component';
import { ConfiguracoesNotificacaoComponent } from './configuracoes-notificacao.component';

@Component({
  selector: 'app-painel-admin',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    GestaoAdminsComponent,
    GestaoGerentesComponent,
    ConfiguracoesNotificacaoComponent
  ],
  templateUrl: './painel-admin.component.html',
  styleUrls: ['./painel-admin.component.css']
})
export class PainelAdminComponent implements OnInit {
  usuarios: UsuarioResumo[] = [];
  admins: AdminSistema[] = [];
  gerentes: GerenteDepartamento[] = [];

  carregando = true;
  mensagemFeedback = '';
  tipoFeedback: 'sucesso' | 'erro' = 'sucesso';

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.carregarDados();
  }

  carregarDados(): void {
    this.carregando = true;
    forkJoin({
      usuarios: this.adminService.listarUsuarios(),
      admins: this.adminService.listarAdmins(),
      gerentes: this.adminService.listarGerentes()
    }).subscribe({
      next: ({ usuarios, admins, gerentes }) => {
        this.usuarios = usuarios;
        this.admins = admins;
        this.gerentes = gerentes;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.exibirFeedback('Erro ao carregar dados administrativos.', 'erro');
      }
    });
  }

  onAlterado(mensagem: string): void {
    const erro = mensagem.toLowerCase().startsWith('erro');
    this.exibirFeedback(mensagem, erro ? 'erro' : 'sucesso');
    this.carregarDados();
  }

  exibirFeedback(msg: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagemFeedback = msg;
    this.tipoFeedback = tipo;
    setTimeout(() => this.limparFeedback(), 5000);
  }

  limparFeedback(): void {
    this.mensagemFeedback = '';
  }
}
