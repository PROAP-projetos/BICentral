import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import type { EChartsOption } from 'echarts';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { RankingService } from '../services/ranking.service';
import { TarefaGrafo, TarefasService } from '../services/tarefas.service';

export interface UgFiltro {
  id: string;
  sigla: string;
  nome: string;
  cor: string;
  ativa: boolean;
}

export interface AtividadeNode {
  id: string;
  label: string;
  ugId: string;
  tipo: 'ug' | 'atividade';
  status?: 'concluida' | 'em_andamento' | 'atrasada';
  responsavel?: string;
  percentual?: number;
}

export interface ArestaLink {
  source: string;
  target: string;
}

const PALETA_CORES = ['#10b981', '#0284c7', '#8b5cf6', '#f59e0b', '#ec4899', '#14b8a6', '#f43f5e', '#6366f1'];
const QTD_UGS_ATIVAS_POR_PADRAO = 5;

@Component({
  selector: 'app-grafo-atividades',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })],
  templateUrl: './grafo-atividades.component.html',
  styleUrls: ['./grafo-atividades.component.css']
})
export class GrafoAtividadesComponent implements OnInit {
  @Output() selecionarAtividade = new EventEmitter<string>();

  layoutGrafo: 'force' | 'circular' = 'force';
  noSelecionadoInfo: AtividadeNode | null = null;
  carregando = false;
  erro = '';

  ugsFiltro: UgFiltro[] = [];

  echartsOptions: EChartsOption = {};

  constructor(
    private rankingService: RankingService,
    private tarefasService: TarefasService
  ) {}

  ngOnInit(): void {
    this.carregarUgs();
  }

  private carregarUgs(): void {
    this.carregando = true;
    this.erro = '';

    this.rankingService.listarRanking('UG').subscribe({
      next: (deptos) => {
        this.ugsFiltro = deptos.map((d, i) => ({
          id: d.departamento,
          sigla: this.extrairSiglaUg(d.departamento),
          nome: d.departamento,
          cor: PALETA_CORES[i % PALETA_CORES.length],
          ativa: i < QTD_UGS_ATIVAS_POR_PADRAO
        }));
        this.atualizarGrafo();
      },
      error: () => {
        this.erro = 'Não foi possível carregar as unidades gestoras agora.';
        this.carregando = false;
      }
    });
  }

  private extrairSiglaUg(nomeCompleto: string): string {
    const partes = nomeCompleto.split(' - ');
    return partes.length > 1 ? partes[partes.length - 1].trim() : nomeCompleto;
  }

  get temUgAtiva(): boolean {
    return this.ugsFiltro.some(u => u.ativa);
  }

  toggleUgFiltro(ug: UgFiltro): void {
    ug.ativa = !ug.ativa;
    this.atualizarGrafo();
  }

  alternarLayout(layout: 'force' | 'circular'): void {
    this.layoutGrafo = layout;
    this.montarOpcoesEcharts();
  }

  onChartClick(params: any): void {
    if (params && params.data && params.data.noOriginal) {
      const node: AtividadeNode = params.data.noOriginal;
      this.noSelecionadoInfo = node;
      if (node.tipo === 'atividade') {
        this.selecionarAtividade.emit(`Quais são os detalhes da tarefa "${node.label}" da ${node.ugId}?`);
      } else {
        this.selecionarAtividade.emit(`Faça uma análise geral do desempenho e atividades da ${node.ugId}.`);
      }
    }
  }

  private nosAtuais: AtividadeNode[] = [];
  private arestasAtuais: ArestaLink[] = [];

  // Busca as tarefas reais de cada UG ativa e monta os nós/arestas do grafo — sem nenhuma relação
  // inventada entre departamentos, porque esse dado não existe em lugar nenhum do banco hoje.
  private atualizarGrafo(): void {
    const ugsAtivas = this.ugsFiltro.filter(u => u.ativa);

    if (ugsAtivas.length === 0) {
      this.nosAtuais = [];
      this.arestasAtuais = [];
      this.montarOpcoesEcharts();
      this.carregando = false;
      return;
    }

    this.carregando = true;

    const chamadas = ugsAtivas.map(ug =>
      this.tarefasService.listarPorDepartamento(ug.id).pipe(
        catchError(() => of([] as TarefaGrafo[]))
      )
    );

    forkJoin(chamadas).subscribe((resultadosPorUg) => {
      const nos: AtividadeNode[] = [];
      const arestas: ArestaLink[] = [];

      ugsAtivas.forEach((ug, i) => {
        const ugNodeId = `UG_${ug.id}`;
        nos.push({ id: ugNodeId, label: ug.sigla, ugId: ug.id, tipo: 'ug' });

        resultadosPorUg[i].forEach((tarefa, j) => {
          const taskId = `ACT_${ug.id}_${j}`;
          nos.push({
            id: taskId,
            label: tarefa.titulo,
            ugId: ug.id,
            tipo: 'atividade',
            status: tarefa.status,
            responsavel: tarefa.responsavel || undefined,
            percentual: tarefa.percentual ?? undefined
          });
          arestas.push({ source: ugNodeId, target: taskId });
        });
      });

      this.nosAtuais = nos;
      this.arestasAtuais = arestas;
      this.montarOpcoesEcharts();
      this.carregando = false;
    });
  }

  private montarOpcoesEcharts(): void {
    const mapaCores = new Map<string, string>(this.ugsFiltro.map(u => [u.id, u.cor]));

    const echartsNodes = this.nosAtuais.map(n => {
      const corUg = mapaCores.get(n.ugId) || '#38bdf8';
      const isUg = n.tipo === 'ug';

      return {
        id: n.id,
        name: n.label,
        symbolSize: isUg ? 46 : 24,
        value: n.percentual ?? 100,
        noOriginal: n,
        itemStyle: {
          color: isUg ? corUg : this.ajustarOpacidadeCor(corUg, 0.85),
          borderColor: isUg ? '#ffffff' : corUg,
          borderWidth: isUg ? 3 : 1.5,
          shadowBlur: isUg ? 15 : 6,
          shadowColor: corUg
        },
        label: {
          show: true,
          position: (isUg ? 'inside' : 'right') as 'inside' | 'right',
          fontSize: isUg ? 11 : 10,
          fontWeight: isUg ? ('bold' as const) : ('normal' as const),
          color: isUg ? '#ffffff' : 'inherit'
        }
      };
    });

    const echartsLinks = this.arestasAtuais.map(a => ({
      source: a.source,
      target: a.target,
      lineStyle: {
        width: 1,
        color: 'rgba(148, 163, 184, 0.4)',
        curveness: 0.05
      }
    }));

    this.echartsOptions = {
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15, 23, 42, 0.9)',
        borderColor: 'rgba(56, 189, 248, 0.3)',
        borderWidth: 1,
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params: any) => {
          if (params.dataType === 'node' && params.data.noOriginal) {
            const no: AtividadeNode = params.data.noOriginal;
            if (no.tipo === 'ug') {
              return `<strong>Unidade Gestora</strong><br/>${no.ugId}`;
            }
            const statusTexto = no.status === 'concluida' ? '🟢 Concluída' : no.status === 'atrasada' ? '🔴 Atrasada' : '🔵 Em Andamento';
            return `
              <div style="padding: 2px 4px;">
                <div style="font-weight: 700; color: #38bdf8; font-size: 13px;">${no.label}</div>
                <div style="font-size: 11px; margin-top: 4px;">Departamento: <strong>${no.ugId}</strong></div>
                <div style="font-size: 11px;">Responsável: <strong>${no.responsavel || 'N/A'}</strong></div>
                <div style="font-size: 11px;">Status: <strong>${statusTexto}</strong> (${no.percentual ?? '—'}%)</div>
              </div>
            `;
          }
          return '';
        }
      },
      series: [
        {
          type: 'graph',
          layout: this.layoutGrafo,
          data: echartsNodes,
          links: echartsLinks,
          roam: true,
          draggable: true,
          focusNodeAdjacency: true,
          force: {
            repulsion: 220,
            edgeLength: [60, 120],
            gravity: 0.15
          },
          emphasis: {
            focus: 'adjacency',
            lineStyle: {
              width: 3
            }
          }
        }
      ]
    };
  }

  private ajustarOpacidadeCor(hex: string, alpha: number): string {
    if (/^#([A-Fa-f0-9]{6})$/.test(hex)) {
      const r = parseInt(hex.slice(1, 3), 16);
      const g = parseInt(hex.slice(3, 5), 16);
      const b = parseInt(hex.slice(5, 7), 16);
      return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }
    return hex;
  }
}
