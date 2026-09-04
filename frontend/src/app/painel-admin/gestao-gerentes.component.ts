import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, GerenteDepartamento, UsuarioResumo } from '../services/admin.service';
import { AgentService } from '../services/agent.service';

export interface GerenteAgrupado {
  usuarioId: number;
  usuarioNome: string | null;
  usuarioEmail: string | null;
  vinculos: GerenteDepartamento[];
}

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

  readonly DEPARTAMENTOS_POR_PAGINA = 6;
  private paginaPorUsuario = new Map<number, number>();

  get departamentosDisponiveis(): string[] {
    const jaCadastrados = new Set(this.gerentes.map((g) => g.departamento));
    return this.departamentos.filter((dep) => !jaCadastrados.has(dep));
  }

  get gerentesAgrupados(): GerenteAgrupado[] {
    const grupos = new Map<number, GerenteAgrupado>();
    for (const vinculo of this.gerentes) {
      let grupo = grupos.get(vinculo.usuarioId);
      if (!grupo) {
        grupo = {
          usuarioId: vinculo.usuarioId,
          usuarioNome: vinculo.usuarioNome,
          usuarioEmail: vinculo.usuarioEmail,
          vinculos: []
        };
        grupos.set(vinculo.usuarioId, grupo);
      }
      grupo.vinculos.push(vinculo);
    }
    return Array.from(grupos.values()).sort((a, b) =>
      (a.usuarioNome || '').localeCompare(b.usuarioNome || '')
    );
  }

  totalPaginas(grupo: GerenteAgrupado): number {
    return Math.max(1, Math.ceil(grupo.vinculos.length / this.DEPARTAMENTOS_POR_PAGINA));
  }

  paginaAtual(usuarioId: number): number {
    return this.paginaPorUsuario.get(usuarioId) ?? 0;
  }

  departamentosVisiveis(grupo: GerenteAgrupado): GerenteDepartamento[] {
    const pagina = Math.min(this.paginaAtual(grupo.usuarioId), this.totalPaginas(grupo) - 1);
    const inicio = pagina * this.DEPARTAMENTOS_POR_PAGINA;
    return grupo.vinculos.slice(inicio, inicio + this.DEPARTAMENTOS_POR_PAGINA);
  }

  mudarPagina(grupo: GerenteAgrupado, delta: number): void {
    const total = this.totalPaginas(grupo);
    const atual = this.paginaAtual(grupo.usuarioId);
    const proxima = (atual + delta + total) % total;
    this.paginaPorUsuario.set(grupo.usuarioId, proxima);
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
