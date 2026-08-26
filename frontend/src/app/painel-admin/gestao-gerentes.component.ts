import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, GerenteDepartamento, UsuarioResumo } from '../services/admin.service';
import { AgentService } from '../services/agent.service';

@Component({
  selector: 'app-gestao-gerentes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-gerentes.component.html',
  styleUrls: ['./gestao-gerentes.component.css']
})
export class GestaoGerentesComponent {
  @Input() gerentes: GerenteDepartamento[] = [];
  @Input() usuarios: UsuarioResumo[] = [];
  @Output() alterado = new EventEmitter<string>();

  usuarioId: number | null = null;
  departamentosSelecionados: string[] = [];
  salvando = false;
  departamentos: string[] = [];
  dropdownAberto = false;

  departamentoParaClassificar: string | null = null;
  tipoParaClassificar: 'UA' | 'UG' = 'UA';
  classificando = false;

  get departamentosDisponiveis(): string[] {
    const jaCadastrados = new Set(this.gerentes.map((g) => g.departamento));
    return this.departamentos.filter((dep) => !jaCadastrados.has(dep));
  }

  constructor(private adminService: AdminService, private agentService: AgentService) {
    this.agentService.listarDepartamentosRelatorio().subscribe({
      next: (lista) => this.departamentos = lista.filter((dep) => dep && dep.trim().length > 0),
      error: () => this.departamentos = []
    });
  }

  adicionar(): void {
    if (!this.usuarioId || this.departamentosSelecionados.length === 0 || this.salvando) return;

    this.salvando = true;
    let pendentes = this.departamentosSelecionados.length;
    let houveErro = false;

    this.departamentosSelecionados.forEach((departamento) => {
      this.adminService.adicionarGerente(this.usuarioId!, departamento).subscribe({
        next: () => {
          pendentes--;
          if (pendentes === 0) this.finalizarAdicao(houveErro);
        },
        error: () => {
          houveErro = true;
          pendentes--;
          if (pendentes === 0) this.finalizarAdicao(houveErro);
        }
      });
    });
  }

  private finalizarAdicao(houveErro: boolean): void {
    this.usuarioId = null;
    this.departamentosSelecionados = [];
    this.salvando = false;
    this.alterado.emit(houveErro ? 'Alguns vínculos falharam ao adicionar.' : 'Vínculo(s) de gerente adicionado(s).');
  }

  toggleDepartamento(dep: string): void {
    const indice = this.departamentosSelecionados.indexOf(dep);
    if (indice === -1) {
      this.departamentosSelecionados = [...this.departamentosSelecionados, dep];
    } else {
      this.departamentosSelecionados = this.departamentosSelecionados.filter((d) => d !== dep);
    }
  }

  classificar(): void {
    if (!this.departamentoParaClassificar || this.classificando) return;

    this.classificando = true;
    this.adminService.classificarDepartamento(this.departamentoParaClassificar, this.tipoParaClassificar).subscribe({
      next: () => {
        this.classificando = false;
        this.alterado.emit(`Departamento classificado como ${this.tipoParaClassificar}.`);
        this.departamentoParaClassificar = null;
        this.tipoParaClassificar = 'UA';
      },
      error: () => {
        this.classificando = false;
        this.alterado.emit('Erro ao classificar departamento.');
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
