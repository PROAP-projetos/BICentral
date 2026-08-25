import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';

export interface UgRankingItem {
  id: string;
  sigla: string;
  nome: string;
  posicao: number;
  posicaoAnterior: number;
  percentual: number;
  percentualAnterior: number;
  totalTarefas: number;
  tarefasConcluidas: number;
  variacaoPosicao: number; // Ex: +2 (subiu 2 posições), -1 (caiu 1), 0 (manteve)
  subiu: boolean;
  caiu: boolean;
  destaqueAnimacao: boolean;
}

export interface AtividadeRecenteItem {
  id: string;
  titulo: string;
  ugSigla: string;
  ugNome: string;
  responsavel: string;
  tempoRelativo: string;
  status: 'concluida' | 'atencao' | 'em_andamento';
  tag: string;
}

export interface InsightIaItem {
  icone: string;
  titulo: string;
  prompt: string;
  badge: string;
}

@Component({
  selector: 'app-leaderboard-ug',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })],
  templateUrl: './leaderboard-ug.component.html',
  styleUrls: ['./leaderboard-ug.component.css']
})
export class LeaderboardUgComponent implements OnInit, OnDestroy {

  @Output() selecionarUg = new EventEmitter<string>();

  modoVisualizacao: 'cards' | 'echarts' = 'cards';
  autoSimulacaoAtiva = false;
  private timerAutoSimulacao?: number;

  // Lista inicial de UGs (Unidades Gestoras/Departamentos)
  ugs: UgRankingItem[] = [
    {
      id: 'PROEST',
      sigla: 'PROEST',
      nome: 'Pró-Reitoria de Assistência Estudantil',
      posicao: 1,
      posicaoAnterior: 1,
      percentual: 88.5,
      percentualAnterior: 88.5,
      totalTarefas: 42,
      tarefasConcluidas: 37,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'PROAD',
      sigla: 'PROAD',
      nome: 'Pró-Reitoria de Administração e Finanças',
      posicao: 2,
      posicaoAnterior: 2,
      percentual: 79.2,
      percentualAnterior: 79.2,
      totalTarefas: 55,
      tarefasConcluidas: 43,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'PROEX',
      sigla: 'PROEX',
      nome: 'Pró-Reitoria de Extensão e Cultura',
      posicao: 3,
      posicaoAnterior: 3,
      percentual: 74.0,
      percentualAnterior: 74.0,
      totalTarefas: 38,
      tarefasConcluidas: 28,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'DTI',
      sigla: 'DTI',
      nome: 'Diretoria de Tecnologia da Informação',
      posicao: 4,
      posicaoAnterior: 4,
      percentual: 68.4,
      percentualAnterior: 68.4,
      totalTarefas: 60,
      tarefasConcluidas: 41,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'PROGRAD',
      sigla: 'PROGRAD',
      nome: 'Pró-Reitoria de Graduação',
      posicao: 5,
      posicaoAnterior: 5,
      percentual: 62.1,
      percentualAnterior: 62.1,
      totalTarefas: 50,
      tarefasConcluidas: 31,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'PROPESQ',
      sigla: 'PROPESQ',
      nome: 'Pró-Reitoria de Pesquisa e Pós-Graduação',
      posicao: 6,
      posicaoAnterior: 6,
      percentual: 55.8,
      percentualAnterior: 55.8,
      totalTarefas: 35,
      tarefasConcluidas: 19,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    },
    {
      id: 'ASCOM',
      sigla: 'ASCOM',
      nome: 'Assessoria de Comunicação Social',
      posicao: 7,
      posicaoAnterior: 7,
      percentual: 42.3,
      percentualAnterior: 42.3,
      totalTarefas: 24,
      tarefasConcluidas: 10,
      variacaoPosicao: 0,
      subiu: false,
      caiu: false,
      destaqueAnimacao: false
    }
  ];

  // Feed em tempo real de acontecimentos recentes do PAT
  atividadesRecentes: AtividadeRecenteItem[] = [
    {
      id: 'atv-1',
      titulo: 'Publicação do Edital do Programa de Permanência Estudantil 2026',
      ugSigla: 'PROEST',
      ugNome: 'Assistência Estudantil',
      responsavel: 'Mariana Silva',
      tempoRelativo: 'há 1h',
      status: 'concluida',
      tag: 'Meta PAT 1.2'
    },
    {
      id: 'atv-2',
      titulo: 'Homologação do Pregão Eletrônico de Servidores em Nuvem',
      ugSigla: 'DTI',
      ugNome: 'Tecnologia da Informação',
      responsavel: 'Carlos Mendes',
      tempoRelativo: 'há 3h',
      status: 'concluida',
      tag: 'Infraestrutura'
    },
    {
      id: 'atv-3',
      titulo: 'Consolidação da Prestação de Contas Orçamentárias',
      ugSigla: 'PROAD',
      ugNome: 'Administração e Finanças',
      responsavel: 'Roberto Lima',
      tempoRelativo: 'hoje',
      status: 'concluida',
      tag: 'Orçamento'
    },
    {
      id: 'atv-4',
      titulo: 'Homologação das Novas Matrizes Curriculares',
      ugSigla: 'PROGRAD',
      ugNome: 'Graduação',
      responsavel: 'Felipe Santos',
      tempoRelativo: 'ontem',
      status: 'concluida',
      tag: 'Ensino'
    },
    {
      id: 'atv-5',
      titulo: 'Aquisição de Insumos para Laboratórios de Pesquisa',
      ugSigla: 'PROPESQ',
      ugNome: 'Pesquisa',
      responsavel: 'Dra. Helena Costa',
      tempoRelativo: 'Prazo: 3 dias',
      status: 'atencao',
      tag: 'Atenção'
    }
  ];

  // Perguntas rápidas com IA (Chips interativos)
  insightsSugeridos: InsightIaItem[] = [
    {
      icone: '🎯',
      titulo: 'Diagnóstico da PROAD',
      prompt: 'Faça um diagnóstico detalhado do desempenho, gargalos e projeção de metas da PROAD.',
      badge: 'Análise Crítica'
    },
    {
      icone: '📊',
      titulo: 'Comparativo PROEST vs DTI',
      prompt: 'Compare a evolução de entregas do PAT entre a PROEST e a DTI.',
      badge: 'Comparativo'
    },
    {
      icone: '⚠️',
      titulo: 'Alertas de Atraso Global',
      prompt: 'Quais atividades e departamentos apresentam maior risco de atraso no PAT 2026?',
      badge: 'Risco'
    },
    {
      icone: '📄',
      titulo: 'Resumo Executivo do PAT',
      prompt: 'Gere um relatório executivo consolidado com o status de todas as UGs do PROAP.',
      badge: 'Relatório'
    }
  ];

  // Opções do ECharts para o modo alternativo
  echartsOptions: any;

  ngOnInit(): void {
    this.atualizarEchartsOptions();
  }

  ngOnDestroy(): void {
    this.pararAutoSimulacao();
  }

  // Getters para KPIs no topo
  get percentualGlobal(): number {
    if (!this.ugs.length) return 0;
    const soma = this.ugs.reduce((acc, ug) => acc + ug.percentual, 0);
    return Math.round((soma / this.ugs.length) * 10) / 10;
  }

  get totalTarefasGlobal(): number {
    return this.ugs.reduce((acc, ug) => acc + ug.totalTarefas, 0);
  }

  get totalConcluidasGlobal(): number {
    return this.ugs.reduce((acc, ug) => acc + ug.tarefasConcluidas, 0);
  }

  get totalUgsEmAtencao(): number {
    return this.ugs.filter(u => u.percentual < 60).length;
  }

  simularReordenacao(): void {
    const posicoesAnterioresMap = new Map<string, number>();
    this.ugs.forEach((ug, idx) => {
      ug.posicaoAnterior = idx + 1;
      posicoesAnterioresMap.set(ug.id, idx + 1);
      ug.percentualAnterior = ug.percentual;
    });

    this.ugs.forEach((ug) => {
      const delta = Math.floor(Math.random() * 30) - 12;
      let novoPct = Math.min(100, Math.max(20, Math.round((ug.percentual + delta) * 10) / 10));
      ug.percentual = novoPct;
      ug.tarefasConcluidas = Math.min(ug.totalTarefas, Math.round((novoPct / 100) * ug.totalTarefas));
    });

    this.ugs.sort((a, b) => b.percentual - a.percentual);

    this.ugs.forEach((ug, idx) => {
      const novaPosicao = idx + 1;
      const antigaPosicao = posicoesAnterioresMap.get(ug.id) || novaPosicao;
      
      ug.posicao = novaPosicao;
      ug.variacaoPosicao = antigaPosicao - novaPosicao;
      ug.subiu = ug.variacaoPosicao > 0;
      ug.caiu = ug.variacaoPosicao < 0;

      if (ug.subiu) {
        ug.destaqueAnimacao = true;
        setTimeout(() => ug.destaqueAnimacao = false, 2500);
      }
    });

    this.atualizarEchartsOptions();
  }

  toggleAutoSimulacao(): void {
    this.autoSimulacaoAtiva = !this.autoSimulacaoAtiva;
    if (this.autoSimulacaoAtiva) {
      this.simularReordenacao();
      this.timerAutoSimulacao = window.setInterval(() => this.simularReordenacao(), 4000);
    } else {
      this.pararAutoSimulacao();
    }
  }

  private pararAutoSimulacao(): void {
    if (this.timerAutoSimulacao) {
      window.clearInterval(this.timerAutoSimulacao);
      this.timerAutoSimulacao = undefined;
    }
  }

  alternarModo(modo: 'cards' | 'echarts'): void {
    this.modoVisualizacao = modo;
  }

  getMedalhaEmoji(posicao: number): string {
    if (posicao === 1) return '🥇';
    if (posicao === 2) return '🥈';
    if (posicao === 3) return '🥉';
    return `#${posicao}`;
  }

  getClasseCorPct(percentual: number): string {
    if (percentual >= 75) return 'pct-alto';
    if (percentual >= 50) return 'pct-medio';
    return 'pct-baixo';
  }

  clicarUg(ug: UgRankingItem): void {
    this.selecionarUg.emit(`Faça uma análise detalhada do desempenho e metas da ${ug.sigla} (${ug.nome}).`);
  }

  clicarAtividade(atv: AtividadeRecenteItem): void {
    this.selecionarUg.emit(`Explique o status e o impacto da entrega da ${atv.ugSigla}: "${atv.titulo}".`);
  }

  clicarInsight(insight: InsightIaItem): void {
    this.selecionarUg.emit(insight.prompt);
  }

  private atualizarEchartsOptions(): void {
    const sorted = [...this.ugs].reverse();
    const categorias = sorted.map(u => u.sigla);
    const valores = sorted.map(u => u.percentual);

    this.echartsOptions = {
      animationDuration: 1000,
      animationDurationUpdate: 1000,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: '{b}: {c}% de execução'
      },
      grid: {
        top: 20,
        bottom: 30,
        left: 80,
        right: 40
      },
      xAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.08)' } }
      },
      yAxis: {
        type: 'category',
        data: categorias,
        inverse: true,
        axisLabel: { color: '#f8fafc', fontWeight: 'bold' }
      },
      series: [
        {
          name: 'Execução PAT',
          type: 'bar',
          data: valores,
          realtimeSort: true,
          label: {
            show: true,
            position: 'right',
            valueAnimation: true,
            formatter: '{c}%',
            color: '#38bdf8',
            fontWeight: 'bold'
          },
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: '#0284c7' },
                { offset: 1, color: '#38bdf8' }
              ]
            },
            borderRadius: [0, 6, 6, 0]
          }
        }
      ]
    };
  }

  trackByUgId(index: number, item: UgRankingItem): string {
    return item.id;
  }
}
