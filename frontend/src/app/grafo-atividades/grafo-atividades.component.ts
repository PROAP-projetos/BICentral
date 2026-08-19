import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import type { EChartsOption } from 'echarts';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';

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
  relacao?: string;
}

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

  ugsFiltro: UgFiltro[] = [
    { id: 'PROEST', sigla: 'PROEST', nome: 'Assistência Estudantil', cor: '#10b981', ativa: true },
    { id: 'PROAD', sigla: 'PROAD', nome: 'Administração e Finanças', cor: '#0284c7', ativa: true },
    { id: 'DTI', sigla: 'DTI', nome: 'Tecnologia da Informação', cor: '#8b5cf6', ativa: true },
    { id: 'PROGRAD', sigla: 'PROGRAD', nome: 'Graduação', cor: '#f59e0b', ativa: true },
    { id: 'PROEX', sigla: 'PROEX', nome: 'Extensão e Cultura', cor: '#ec4899', ativa: true }
  ];

  private readonly todosNoAtividades: AtividadeNode[] = [
    // UGs Hubs
    { id: 'UG_PROEST', label: 'PROEST', ugId: 'PROEST', tipo: 'ug' },
    { id: 'UG_PROAD', label: 'PROAD', ugId: 'PROAD', tipo: 'ug' },
    { id: 'UG_DTI', label: 'DTI', ugId: 'DTI', tipo: 'ug' },
    { id: 'UG_PROGRAD', label: 'PROGRAD', ugId: 'PROGRAD', tipo: 'ug' },
    { id: 'UG_PROEX', label: 'PROEX', ugId: 'PROEX', tipo: 'ug' },

    // Atividades PROEST
    { id: 'ACT_PROEST_1', label: 'Auxílio Permanência 2026', ugId: 'PROEST', tipo: 'atividade', status: 'em_andamento', responsavel: 'Dra. Márcia Silva', percentual: 88 },
    { id: 'ACT_PROEST_2', label: 'Restaurante Universitário', ugId: 'PROEST', tipo: 'atividade', status: 'concluida', responsavel: 'Nutr. Carlos Eduardo', percentual: 100 },
    { id: 'ACT_PROEST_3', label: 'Bolsa Atleta Estudantil', ugId: 'PROEST', tipo: 'atividade', status: 'em_andamento', responsavel: 'Prof. Roberto Nunes', percentual: 72 },

    // Atividades PROAD
    { id: 'ACT_PROAD_1', label: 'Pregão Eletrônico Laptops', ugId: 'PROAD', tipo: 'atividade', status: 'concluida', responsavel: 'Lic. Fernando Lima', percentual: 100 },
    { id: 'ACT_PROAD_2', label: 'Reforma Bloco Reitoria', ugId: 'PROAD', tipo: 'atividade', status: 'atrasada', responsavel: 'Eng. Renato Souza', percentual: 45 },
    { id: 'ACT_PROAD_3', label: 'Relatório Gestão Fiscal', ugId: 'PROAD', tipo: 'atividade', status: 'em_andamento', responsavel: 'Cont. Patricia Dias', percentual: 79 },

    // Atividades DTI
    { id: 'ACT_DTI_1', label: 'Integração API BICentral', ugId: 'DTI', tipo: 'atividade', status: 'em_andamento', responsavel: 'Dev. Lean & Dallyla', percentual: 82 },
    { id: 'ACT_DTI_2', label: 'Migração Supabase Cloud', ugId: 'DTI', tipo: 'atividade', status: 'concluida', responsavel: 'SysAdmin Lucas', percentual: 100 },
    { id: 'ACT_DTI_3', label: 'Expansão Wi-Fi Câmpus', ugId: 'DTI', tipo: 'atividade', status: 'em_andamento', responsavel: 'Redes Gabriel', percentual: 68 },

    // Atividades PROGRAD
    { id: 'ACT_PROGRAD_1', label: 'Revisão Projetos Pedagógicos', ugId: 'PROGRAD', tipo: 'atividade', status: 'em_andamento', responsavel: 'Prof. Ana Paula', percentual: 75 },
    { id: 'ACT_PROGRAD_2', label: 'Matrícula Verão 2026', ugId: 'PROGRAD', tipo: 'atividade', status: 'concluida', responsavel: 'Coord. Sérgio', percentual: 100 },
    { id: 'ACT_PROGRAD_3', label: 'Acompanhamento ENADE', ugId: 'PROGRAD', tipo: 'atividade', status: 'em_andamento', responsavel: 'Dra. Beatriz', percentual: 60 },

    // Atividades PROEX
    { id: 'ACT_PROEX_1', label: 'Edital Projetos Extensão', ugId: 'PROEX', tipo: 'atividade', status: 'concluida', responsavel: 'Coord. Mariana', percentual: 100 },
    { id: 'ACT_PROEX_2', label: 'Feira de Ciências UFT', ugId: 'PROEX', tipo: 'atividade', status: 'em_andamento', responsavel: 'Prof. Thiago', percentual: 64 }
  ];

  private readonly todasArestas: ArestaLink[] = [
    // Conexões UG -> Atividades
    { source: 'UG_PROEST', target: 'ACT_PROEST_1' },
    { source: 'UG_PROEST', target: 'ACT_PROEST_2' },
    { source: 'UG_PROEST', target: 'ACT_PROEST_3' },

    { source: 'UG_PROAD', target: 'ACT_PROAD_1' },
    { source: 'UG_PROAD', target: 'ACT_PROAD_2' },
    { source: 'UG_PROAD', target: 'ACT_PROAD_3' },

    { source: 'UG_DTI', target: 'ACT_DTI_1' },
    { source: 'UG_DTI', target: 'ACT_DTI_2' },
    { source: 'UG_DTI', target: 'ACT_DTI_3' },

    { source: 'UG_PROGRAD', target: 'ACT_PROGRAD_1' },
    { source: 'UG_PROGRAD', target: 'ACT_PROGRAD_2' },
    { source: 'UG_PROGRAD', target: 'ACT_PROGRAD_3' },

    { source: 'UG_PROEX', target: 'ACT_PROEX_1' },
    { source: 'UG_PROEX', target: 'ACT_PROEX_2' },

    // Arestas de Parceria/Dependência Cruzada entre UGs
    { source: 'ACT_DTI_1', target: 'ACT_PROAD_3', relacao: 'Integração de Dados Fiscais' },
    { source: 'ACT_DTI_2', target: 'ACT_PROGRAD_2', relacao: 'Infraestrutura de Matrícula' },
    { source: 'ACT_PROAD_1', target: 'ACT_DTI_3', relacao: 'Aquisição de Equipamentos' },
    { source: 'ACT_PROEST_1', target: 'ACT_PROAD_3', relacao: 'Repasse Financeiro' }
  ];

  echartsOptions: EChartsOption = {};

  ngOnInit(): void {
    this.atualizarOpcoesGrafo();
  }

  toggleUgFiltro(ug: UgFiltro): void {
    ug.ativa = !ug.ativa;
    this.atualizarOpcoesGrafo();
  }

  alternarLayout(layout: 'force' | 'circular'): void {
    this.layoutGrafo = layout;
    this.atualizarOpcoesGrafo();
  }

  onChartClick(params: any): void {
    if (params && params.data && params.data.noOriginal) {
      const node: AtividadeNode = params.data.noOriginal;
      this.noSelecionadoInfo = node;
      if (node.tipo === 'atividade') {
        this.selecionarAtividade.emit(`Quais são os detalhes da atividade "${node.label}" da ${node.ugId}?`);
      } else {
        this.selecionarAtividade.emit(`Faça uma análise geral do desempenho e atividades da ${node.ugId}.`);
      }
    }
  }

  private atualizarOpcoesGrafo(): void {
    const ugsAtivasIds = new Set(this.ugsFiltro.filter(u => u.ativa).map(u => u.id));
    const mapaCores = new Map<string, string>(this.ugsFiltro.map(u => [u.id, u.cor]));

    // Filtra nós cujas UGs estão ativas
    const nosFiltrados = this.todosNoAtividades.filter(n => ugsAtivasIds.has(n.ugId));
    const idsNosFiltrados = new Set(nosFiltrados.map(n => n.id));

    // Filtra arestas cujos dois extremos estão ativos
    const arestasFiltradas = this.todasArestas.filter(a => 
      idsNosFiltrados.has(a.source) && idsNosFiltrados.has(a.target)
    );

    // Mapeia nós para o formato ECharts
    const echartsNodes = nosFiltrados.map(n => {
      const corUg = mapaCores.get(n.ugId) || '#38bdf8';
      const isUg = n.tipo === 'ug';

      return {
        id: n.id,
        name: n.label,
        symbolSize: isUg ? 46 : 24,
        value: n.percentual || 100,
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

    const echartsLinks = arestasFiltradas.map(a => ({
      source: a.source,
      target: a.target,
      lineStyle: {
        width: a.relacao ? 2 : 1,
        type: a.relacao ? ('dashed' as const) : ('solid' as const),
        color: a.relacao ? '#38bdf8' : 'rgba(148, 163, 184, 0.4)',
        curveness: a.relacao ? 0.2 : 0.05
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
              return `<strong>Pró-Reitoria / Diretoria</strong><br/>UG: ${no.ugId}`;
            }
            const statusTexto = no.status === 'concluida' ? '🟢 Concluída' : no.status === 'atrasada' ? '🔴 Atrasada' : '🔵 Em Andamento';
            return `
              <div style="padding: 2px 4px;">
                <div style="font-weight: 700; color: #38bdf8; font-size: 13px;">${no.label}</div>
                <div style="font-size: 11px; margin-top: 4px;">Departamento: <strong>${no.ugId}</strong></div>
                <div style="font-size: 11px;">Responsável: <strong>${no.responsavel || 'N/A'}</strong></div>
                <div style="font-size: 11px;">Status: <strong>${statusTexto}</strong> (${no.percentual}%)</div>
              </div>
            `;
          }
          if (params.dataType === 'edge') {
            return params.data.lineStyle.type === 'dashed' 
              ? `<strong>Relação Institucional:</strong> ${params.data.lineStyle.color}`
              : `Vínculo de Atividade`;
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
