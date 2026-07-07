import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, GerenteDepartamento, UsuarioResumo } from '../services/admin.service';

@Component({
  selector: 'app-gestao-gerentes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-gerentes.component.html'
})
export class GestaoGerentesComponent {
  @Input() gerentes: GerenteDepartamento[] = [];
  @Input() usuarios: UsuarioResumo[] = [];
  @Output() alterado = new EventEmitter<string>();

  usuarioId: number | null = null;
  departamento = '';
  tipoUnidade: 'UA' | 'UG' = 'UA';
  salvando = false;

  constructor(private adminService: AdminService) {}

  adicionar(): void {
    const departamento = this.departamento.trim();
    if (!this.usuarioId || !departamento || this.salvando) return;

    this.salvando = true;
    this.adminService.adicionarGerente(this.usuarioId, departamento, this.tipoUnidade).subscribe({
      next: () => {
        this.usuarioId = null;
        this.departamento = '';
        this.tipoUnidade = 'UA';
        this.salvando = false;
        this.alterado.emit('Vinculo de gerente adicionado.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao adicionar vinculo de gerente.');
      }
    });
  }

  remover(gerente: GerenteDepartamento): void {
    if (this.salvando) return;

    this.salvando = true;
    this.adminService.removerGerente(gerente.id).subscribe({
      next: () => {
        this.salvando = false;
        this.alterado.emit('Vinculo de gerente removido.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao remover vinculo de gerente.');
      }
    });
  }
}
