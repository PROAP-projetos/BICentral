import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminSistema, UsuarioResumo } from '../services/admin.service';

@Component({
  selector: 'app-gestao-admins',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-admins.component.html'
})
export class GestaoAdminsComponent {
  @Input() admins: AdminSistema[] = [];
  @Input() usuarios: UsuarioResumo[] = [];
  @Output() alterado = new EventEmitter<string>();

  usuarioId: number | null = null;
  salvando = false;

  constructor(private adminService: AdminService) {}

  get usuariosDisponiveis(): UsuarioResumo[] {
    const idsAdmin = new Set(this.admins.map((admin) => admin.usuarioId));
    return this.usuarios.filter((usuario) => !idsAdmin.has(usuario.id));
  }

  adicionar(): void {
    if (!this.usuarioId || this.salvando) return;

    this.salvando = true;
    this.adminService.enviarConviteAdmin(this.usuarioId).subscribe({
      next: () => {
        this.usuarioId = null;
        this.salvando = false;
        this.alterado.emit('Convite de administrador enviado.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao enviar convite de administrador.');
      }
    });
  }

  remover(admin: AdminSistema): void {
    if (this.salvando) return;

    this.salvando = true;
    this.adminService.removerAdmin(admin.usuarioId).subscribe({
      next: () => {
        this.salvando = false;
        this.alterado.emit('Administrador removido.');
      },
      error: () => {
        this.salvando = false;
        this.alterado.emit('Erro ao remover administrador.');
      }
    });
  }
}
