import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, UsuarioResponsavel, UsuarioResumo } from '../services/admin.service';

@Component({
  selector: 'app-gestao-responsaveis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-responsaveis.component.html',
  styleUrls: ['./gestao-responsaveis.component.css']
})
export class GestaoResponsaveisComponent {
  @Input() responsaveis: UsuarioResponsavel[] = [];
  @Input() usuarios: UsuarioResumo[] = [];
  @Output() alterado = new EventEmitter<string>();

  usuarioId: number | null = null;
  busca = '';
  sugestoes: string[] = [];
  nomeSelecionado: string | null = null;
  buscando = false;
  salvando = false;
  private buscaTimer?: number;

  constructor(private adminService: AdminService) {}

  onBuscaChange(): void {
    this.nomeSelecionado = null;
    window.clearTimeout(this.buscaTimer);

    const termo = this.busca.trim();
    if (termo.length < 2) {
      this.sugestoes = [];
      return;
    }

    this.buscaTimer = window.setTimeout(() => {
      this.buscando = true;
      this.adminService.buscarResponsaveisPat(termo).subscribe({
        next: (lista) => {
          this.sugestoes = lista;
          this.buscando = false;
        },
        error: () => {
          this.sugestoes = [];
          this.buscando = false;
        }
      });
    }, 300);
  }

  selecionarSugestao(nome: string): void {
    this.nomeSelecionado = nome;
    this.busca = nome;
    this.sugestoes = [];
  }

  adicionar(): void {
    if (!this.usuarioId || !this.nomeSelecionado || this.salvando) return;

    this.salvando = true;
    this.adminService.adicionarResponsavel(this.usuarioId, this.nomeSelecionado).subscribe({
      next: () => {
        this.usuarioId = null;
        this.busca = '';
        this.nomeSelecionado = null;
        this.salvando = false;
        this.alterado.emit('Vinculo de responsavel adicionado.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao adicionar vinculo de responsavel.');
      }
    });
  }

  remover(responsavel: UsuarioResponsavel): void {
    if (this.salvando) return;

    this.salvando = true;
    this.adminService.removerResponsavel(responsavel.id).subscribe({
      next: () => {
        this.salvando = false;
        this.alterado.emit('Vinculo de responsavel removido.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao remover vinculo de responsavel.');
      }
    });
  }
}
