import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
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

  departamentosParaClassificar: string[] = [];
  tipoParaClassificar: 'UA' | 'UG' = 'UA';
  classificando = false;
  dropdownClassificarAberto = false;
  buscaClassificar = '';

  readonly DEPARTAMENTOS_POR_PAGINA = 6;
  private paginaPorUsuario = new Map<number, number>();

  // Só esconde um departamento se o usuário selecionado no formulário JÁ é gerente dele
  // (evita duplicar o mesmo vínculo) — um departamento pode ter vários gerentes diferentes,
  // então não filtra pelos vínculos de todo mundo, só pelos do usuário sendo escolhido agora.
  get departamentosDisponiveis(): string[] {
    const jaCadastrados = new Set(
      this.gerentes.filter((g) => g.usuarioId === this.usuarioId).map((g) => g.departamento)
    );
    return this.departamentos.filter((dep) => !jaCadastrados.has(dep));
  }

  get departamentosClassificaveis(): string[] {
    const busca = this.normalizar(this.buscaClassificar);
    return busca ? this.departamentos.filter((dep) => this.normalizar(dep).includes(busca)) : this.departamentos;
  }

  private normalizar(texto: string): string {
    return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
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

  toggleClassificar(dep: string): void {
    this.departamentosParaClassificar = this.departamentosParaClassificar.includes(dep)
      ? this.departamentosParaClassificar.filter((d) => d !== dep)
      : [...this.departamentosParaClassificar, dep];
  }

  selecionarTodosVisiveis(): void {
    this.departamentosParaClassificar = Array.from(
      new Set([...this.departamentosParaClassificar, ...this.departamentosClassificaveis])
    );
  }

  limparSelecaoClassificar(): void {
    this.departamentosParaClassificar = [];
  }

  alternarDropdownClassificar(): void {
    this.dropdownClassificarAberto = !this.dropdownClassificarAberto;
    if (this.dropdownClassificarAberto) this.dropdownAberto = false;
  }

  alternarDropdownGerentes(): void {
    this.dropdownAberto = !this.dropdownAberto;
    if (this.dropdownAberto) this.dropdownClassificarAberto = false;
  }

  classificar(): void {
    if (this.departamentosParaClassificar.length === 0 || this.classificando) return;

    const tipo = this.tipoParaClassificar;
    const alvos = [...this.departamentosParaClassificar];
    this.classificando = true;

    forkJoin(
      alvos.map((departamento) =>
        this.adminService.classificarDepartamento(departamento, tipo).pipe(
          catchError(() => of('erro' as const))
        )
      )
    ).subscribe((resultados) => {
      const falhas = resultados.filter((r) => r === 'erro').length;
      const ok = alvos.length - falhas;
      this.classificando = false;
      this.departamentosParaClassificar = alvos.filter((_, i) => resultados[i] === 'erro');
      this.buscaClassificar = '';
      if (falhas === 0) {
        this.dropdownClassificarAberto = false;
        this.tipoParaClassificar = 'UA';
      }
      this.alterado.emit(
        falhas === 0
          ? `${ok} departamento(s) classificado(s) como ${tipo}.`
          : `${ok} classificado(s) como ${tipo}; ${falhas} falharam e continuam selecionados.`
      );
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
