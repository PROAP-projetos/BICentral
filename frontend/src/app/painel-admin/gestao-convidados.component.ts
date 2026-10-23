import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService, Convidado } from '../services/admin.service';

@Component({
  selector: 'app-gestao-convidados',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './gestao-convidados.component.html',
  styleUrls: ['./painel-admin.component.css'],
  styles: [`
    .convidado-row { grid-template-columns: minmax(200px, 1.4fr) minmax(160px, 1fr) auto; }
    .hero a.btn { text-decoration: none; display: inline-block; white-space: nowrap; }
    .pendente-texto { font-size: 0.78rem; color: #b45309; font-weight: 600; }
    .ativo-texto { font-size: 0.78rem; color: #475569; }
    .role-chip-small.pendente { background: #fef3c7; color: #92400e; }

    @media (max-width: 700px) {
      .table-head.convidado-row { display: none; }
      .convidado-row { grid-template-columns: 1fr; gap: 0.6rem; }
      .actions-col { justify-content: flex-start; }
      .form-compact { flex-direction: column; }
      .form-compact input { min-width: 0; }
    }
  `]
})
export class GestaoConvidadosComponent implements OnInit {
  convidados: Convidado[] = [];
  email = '';
  carregando = true;
  salvando = false;
  mensagem = '';
  tipoMensagem: 'sucesso' | 'erro' = 'sucesso';

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.adminService.listarConvidados().subscribe({
      next: (convidados) => {
        this.convidados = convidados;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.aviso('Erro ao carregar os convidados.', 'erro');
      }
    });
  }

  adicionar(): void {
    const email = this.email.trim();
    if (!email || this.salvando) return;

    this.salvando = true;
    this.adminService.adicionarConvidado(email).subscribe({
      next: (resposta) => {
        this.email = '';
        this.salvando = false;
        this.aviso(resposta.mensagem, 'sucesso');
        this.carregar();
      },
      error: (err) => {
        this.salvando = false;
        this.aviso(err?.error?.mensagem || 'Erro ao adicionar convidado.', 'erro');
      }
    });
  }

  remover(convidado: Convidado): void {
    if (this.salvando) return;

    this.salvando = true;
    const remocao = convidado.pendente
      ? this.adminService.removerConvidadoPendente(convidado.email!)
      : this.adminService.removerConvidado(convidado.usuarioId!);

    remocao.subscribe({
      next: () => {
        this.salvando = false;
        this.aviso('Convidado removido.', 'sucesso');
        this.carregar();
      },
      error: () => {
        this.salvando = false;
        this.aviso('Erro ao remover convidado.', 'erro');
      }
    });
  }

  aviso(msg: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagem = msg;
    this.tipoMensagem = tipo;
    setTimeout(() => (this.mensagem = ''), 5000);
  }
}
