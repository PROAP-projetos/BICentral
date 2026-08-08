import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../services/admin.service';

@Component({
  selector: 'app-configuracoes-notificacao',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracoes-notificacao.component.html'
})
export class ConfiguracoesNotificacaoComponent implements OnInit {
  @Output() alterado = new EventEmitter<string>();

  limiteBaixoPct: number | null = null;
  limiteBomPct: number | null = null;
  carregando = true;
  salvando = false;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.adminService.buscarConfiguracoesNotificacao().subscribe({
      next: (config) => {
        this.limiteBaixoPct = Number(config.limiteBaixoPct);
        this.limiteBomPct = Number(config.limiteBomPct);
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.alterado.emit('Erro ao carregar configuracoes de notificacao.');
      }
    });
  }

  salvar(): void {
    if (this.limiteBaixoPct === null || this.limiteBomPct === null || this.salvando) return;

    this.salvando = true;
    this.adminService.salvarConfiguracoesNotificacao(this.limiteBaixoPct, this.limiteBomPct).subscribe({
      next: (config) => {
        this.limiteBaixoPct = Number(config.limiteBaixoPct);
        this.limiteBomPct = Number(config.limiteBomPct);
        this.salvando = false;
        this.alterado.emit('Configuracoes salvas.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao salvar configuracoes.');
      }
    });
  }
}
