import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService, TesterProiap } from '../services/admin.service';
import { AgentComponent } from '../agent/agent.component';

@Component({
  selector: 'app-gestao-testers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './gestao-testers.component.html',
  styleUrls: ['./painel-admin.component.css'],
  styles: [`
    .tester-row { grid-template-columns: minmax(200px, 1.4fr) minmax(160px, 1fr) auto; }
    .budget-cell { display: flex; flex-direction: column; gap: 0.3rem; }
    .budget-trilho { height: 6px; border-radius: 999px; background: #e2e8f0; overflow: hidden; }
    .budget-preenchido { height: 100%; background: var(--color-primary); border-radius: 999px; }
    .budget-preenchido.esgotado { background: #dc2626; }
    .budget-texto { font-size: 0.78rem; color: #64748b; }
    .hero a.btn { text-decoration: none; display: inline-block; white-space: nowrap; }
    .pendente-texto { font-size: 0.78rem; color: #b45309; font-weight: 600; }
    .role-chip-small.pendente { background: #fef3c7; color: #92400e; }
    .btn-link-inline {
      background: none; border: none; padding: 0; margin-left: 0.4rem;
      color: var(--color-primary); font-size: 0.78rem; cursor: pointer; text-decoration: underline;
    }
    .btn-link-inline:disabled { opacity: 0.5; cursor: default; }
    .form-compact-inline { display: flex; align-items: center; flex-wrap: wrap; gap: 0.1rem; }
    .input-limite { width: 4.5rem; font-size: 0.78rem; padding: 0.1rem 0.3rem; }

    @media (max-width: 700px) {
      .table-head.tester-row { display: none; }
      .tester-row { grid-template-columns: 1fr; gap: 0.6rem; }
      .actions-col { justify-content: flex-start; }
      .form-compact { flex-direction: column; }
      .form-compact input { min-width: 0; }
    }
  `]
})
export class GestaoTestersComponent implements OnInit {
  testers: TesterProiap[] = [];
  email = '';
  carregando = true;
  salvando = false;
  mensagem = '';
  tipoMensagem: 'sucesso' | 'erro' = 'sucesso';

  versaoAgente = AgentComponent.VERSAO_AGENTE;
  versaoJaNotificada = false;
  notificandoVersao = false;

  editandoLimiteId: number | null = null;
  novoLimite: number | null = null;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.carregar();
    this.adminService.statusNotificacaoVersao(this.versaoAgente).subscribe({
      next: (status) => (this.versaoJaNotificada = status.enviado),
      error: () => {}
    });
  }

  notificarVersao(): void {
    if (this.notificandoVersao || this.versaoJaNotificada) return;

    this.notificandoVersao = true;
    this.adminService.notificarVersaoTesters(this.versaoAgente).subscribe({
      next: (resposta) => {
        this.notificandoVersao = false;
        this.versaoJaNotificada = true;
        this.aviso(
          resposta.jaEnviado
            ? 'Esse aviso já tinha sido enviado antes.'
            : `E-mail da versão ${this.versaoAgente} enviado para ${resposta.enviados} tester(s).`,
          'sucesso'
        );
      },
      error: () => {
        this.notificandoVersao = false;
        this.aviso('Erro ao enviar o e-mail de versão.', 'erro');
      }
    });
  }

  carregar(): void {
    this.carregando = true;
    this.adminService.listarTesters().subscribe({
      next: (testers) => {
        this.testers = testers;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.aviso('Erro ao carregar os testers.', 'erro');
      }
    });
  }

  adicionar(): void {
    const email = this.email.trim();
    if (!email || this.salvando) return;

    this.salvando = true;
    this.adminService.adicionarTester(email).subscribe({
      next: (resposta) => {
        this.email = '';
        this.salvando = false;
        this.aviso(resposta.mensagem, 'sucesso');
        this.carregar();
      },
      error: (err) => {
        this.salvando = false;
        this.aviso(err?.error?.mensagem || 'Erro ao adicionar tester.', 'erro');
      }
    });
  }

  remover(tester: TesterProiap): void {
    if (this.salvando) return;

    this.salvando = true;
    const remocao = tester.pendente
      ? this.adminService.removerTesterPendente(tester.email!)
      : this.adminService.removerTester(tester.usuarioId!);

    remocao.subscribe({
      next: () => {
        this.salvando = false;
        this.aviso('Tester removido.', 'sucesso');
        this.carregar();
      },
      error: () => {
        this.salvando = false;
        this.aviso('Erro ao remover tester.', 'erro');
      }
    });
  }

  iniciarEdicaoLimite(t: TesterProiap): void {
    this.editandoLimiteId = t.usuarioId;
    this.novoLimite = t.limite;
  }

  cancelarEdicaoLimite(): void {
    this.editandoLimiteId = null;
    this.novoLimite = null;
  }

  salvarLimite(t: TesterProiap): void {
    if (t.usuarioId == null || this.novoLimite == null || this.novoLimite <= 0 || this.salvando) return;

    this.salvando = true;
    this.adminService.definirLimiteTester(t.usuarioId, this.novoLimite).subscribe({
      next: () => {
        this.salvando = false;
        this.cancelarEdicaoLimite();
        this.aviso('Limite atualizado.', 'sucesso');
        this.carregar();
      },
      error: (err) => {
        this.salvando = false;
        this.aviso(err?.error?.mensagem || 'Erro ao atualizar o limite.', 'erro');
      }
    });
  }

  // Volta pro padrão global (US$ 1,00) — limite null no backend.
  restaurarLimitePadrao(t: TesterProiap): void {
    if (t.usuarioId == null || this.salvando) return;

    this.salvando = true;
    this.adminService.definirLimiteTester(t.usuarioId, null).subscribe({
      next: () => {
        this.salvando = false;
        this.cancelarEdicaoLimite();
        this.aviso('Limite restaurado pro padrão.', 'sucesso');
        this.carregar();
      },
      error: () => {
        this.salvando = false;
        this.aviso('Erro ao restaurar o limite.', 'erro');
      }
    });
  }

  percentual(tester: TesterProiap): number {
    if (!tester.limite) return 0;
    return Math.min(100, (tester.gastoIndividual / tester.limite) * 100);
  }

  aviso(msg: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagem = msg;
    this.tipoMensagem = tipo;
    setTimeout(() => (this.mensagem = ''), 5000);
  }
}
