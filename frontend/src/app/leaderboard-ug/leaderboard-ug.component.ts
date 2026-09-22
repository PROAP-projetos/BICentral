import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { RankingDepartamento, RankingService } from '../services/ranking.service';

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
  carregandoRanking = false;
  erroRanking = '';

  // Preenchido pelo endpoint real, sem limite artificial de UGs.
  ugs: UgRankingItem[] = [];

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

  constructor(private rankingService: RankingService) {}

  ngOnInit(): void {
    this.carregarRanking();
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

  get percentualAcoesConcluidas(): number {
    if (!this.totalTarefasGlobal) return 0;
    return Math.round((this.totalConcluidasGlobal / this.totalTarefasGlobal) * 100);
  }

  carregarRanking(): void {
    this.carregandoRanking = true;
    this.erroRanking = '';

    // Sem `limite`: o painel sempre exibe todas as UGs devolvidas pela API.
    this.rankingService.listarRanking('UG').subscribe({
      next: (ranking) => {
        this.ugs = ranking.map(item => this.paraUgRankingItem(item));
        this.atualizarEchartsOptions();
        this.carregandoRanking = false;
      },
      error: () => {
        this.ugs = [];
        this.atualizarEchartsOptions();
        this.erroRanking = 'Não foi possível carregar o ranking das UGs agora.';
        this.carregandoRanking = false;
      }
    });
  }

  private paraUgRankingItem(item: RankingDepartamento): UgRankingItem {
    const posicaoAnterior = item.posicaoAnterior ?? item.posicaoAtual;
    const variacaoPosicao = posicaoAnterior - item.posicaoAtual;
    const percentual = Math.min(100, Math.max(0, item.mediaExecucaoPct));

    return {
      id: item.departamento,
      sigla: this.extrairSigla(item.departamento),
      nome: item.departamento,
      posicao: item.posicaoAtual,
      posicaoAnterior,
      percentual,
      // O endpoint atual registra a posição anterior, não o percentual anterior.
      percentualAnterior: percentual,
      totalTarefas: item.qtdAcoes,
      tarefasConcluidas: Math.round((item.qtdAcoes * percentual) / 100),
      variacaoPosicao,
      subiu: variacaoPosicao > 0,
      caiu: variacaoPosicao < 0,
      destaqueAnimacao: false
    };
  }

  private extrairSigla(departamento: string): string {
    const partes = departamento.split(' - ');
    return partes.length > 1 ? partes[partes.length - 1].trim() : departamento;
  }

  simularReordenacao(): void {
    if (!this.ugs.length) return;

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
