import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { RankingDepartamento, RankingResumo, RankingService } from '../services/ranking.service';
import { aoMudarTema, lerCoresDoTema } from '../shared/tema';
import { promptAcoesSemExecucao, promptDiagnostico, promptMaiorXMenor, promptResumoExecutivo } from './insights';
import { campusDaCoordenacao, extrairSigla, nomeCurtoCoordenacao } from '../shared/sigla';

export interface UgRankingItem {
  id: string;
  sigla: string;
  nome: string;
  posicao: number;
  posicaoAnterior: number;
  percentual: number;
  percentualAnterior: number;
  totalAcoes: number;
  acoesConcluidas: number;
  semComparacao: boolean;
  variacaoPosicao: number; // Ex: +2 (subiu 2 posições), -1 (caiu 1), 0 (manteve)
  subiu: boolean;
  caiu: boolean;
  destaqueAnimacao: boolean;
}

export interface InsightIaItem {
  icone: string;
  titulo: string;
  prompt: string;
  badge: string;
}

export type FiltroUnidade = 'todos' | 'pro-reitoria' | 'campus' | 'superintendencia' | 'coordenacao' | 'outros';

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
  carregandoRanking = false;
  erroRanking = '';

  // Preenchido pelo endpoint real, sem limite artificial de UGs.
  ugs: UgRankingItem[] = [];

  ugsExibidas: UgRankingItem[] = [];
  filtroAtivo: FiltroUnidade = 'todos';
  readonly filtros: { valor: FiltroUnidade; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todas, exceto coordenações' },
    { valor: 'pro-reitoria', rotulo: 'Pró-Reitorias' },
    { valor: 'campus', rotulo: 'Câmpus' },
    { valor: 'superintendencia', rotulo: 'Superintendências' },
    { valor: 'coordenacao', rotulo: 'Coordenações' },
    { valor: 'outros', rotulo: 'Outras unidades' }
  ];

  campusAtivo = 'todos';
  campi: { valor: string; rotulo: string; total: number }[] = [];

  resumo: RankingResumo | null = null;
  atualizadoEm: string | null = null;

  insightsSugeridos: InsightIaItem[] = [];

  // Opções do ECharts para o modo alternativo
  echartsOptions: any;

  private pararObservarTema?: () => void;

  constructor(private rankingService: RankingService, private host: ElementRef<HTMLElement>) {}

  ngOnInit(): void {
    this.pararObservarTema = aoMudarTema(this.host.nativeElement, () => this.atualizarEchartsOptions());
    this.carregarRanking();
  }

  get unidadesPrincipais(): UgRankingItem[] {
    return this.ugs.filter(u => this.categoria(u.nome) !== 'coordenacao');
  }

  ngOnDestroy(): void {
    this.pararObservarTema?.();
  }

  // Getters para KPIs no topo
  get percentualGlobal(): number {
    const base = this.unidadesPrincipais;
    if (!base.length) return 0;
    const soma = base.reduce((acc, ug) => acc + ug.percentual, 0);
    return Math.round((soma / base.length) * 10) / 10;
  }

  get totalAcoesGlobal(): number {
    return this.resumo?.totalAcoes ?? 0;
  }

  get acoesConcluidasGlobal(): number {
    return this.resumo?.acoesConcluidas ?? 0;
  }

  get totalUgsEmAtencao(): number {
    return this.unidadesPrincipais.filter(u => u.percentual < 60).length;
  }

  get percentualAcoesConcluidas(): number {
    if (!this.totalAcoesGlobal) return 0;
    return Math.round((this.acoesConcluidasGlobal / this.totalAcoesGlobal) * 100);
  }

  carregarRanking(): void {
    this.carregandoRanking = true;
    this.erroRanking = '';

    this.rankingService.atualizacao().subscribe({
      next: (a) => (this.atualizadoEm = a.atualizadoEm),
      error: () => (this.atualizadoEm = null)
    });

    this.rankingService.resumo().subscribe({
      next: (resumo) => (this.resumo = resumo),
      error: () => (this.resumo = null)
    });

    this.rankingService.listarRanking().subscribe({
      next: (ranking) => {
        this.ugs = ranking.map(item => this.paraUgRankingItem(item));
        this.aplicarFiltro();
        this.carregandoRanking = false;
      },
      error: () => {
        this.ugs = [];
        this.resumo = null;
        this.aplicarFiltro();
        this.erroRanking = 'Não foi possível carregar o ranking agora.';
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
      sigla: extrairSigla(item.departamento),
      nome: item.departamento,
      posicao: item.posicaoAtual,
      posicaoAnterior,
      percentual,
      // O endpoint atual registra a posição anterior, não o percentual anterior.
      percentualAnterior: percentual,
      totalAcoes: item.qtdAcoes,
      acoesConcluidas: item.qtdAcoesConcluidas,
      semComparacao: item.posicaoAnterior == null,
      variacaoPosicao,
      subiu: variacaoPosicao > 0,
      caiu: variacaoPosicao < 0,
      destaqueAnimacao: false
    };
  }


  contagemFiltro(filtro: FiltroUnidade): number {
    return filtro === 'todos' ? this.unidadesPrincipais.length : this.ugs.filter(u => this.categoria(u.nome) === filtro).length;
  }

  private calcularCampi(): { valor: string; rotulo: string; total: number }[] {
    const contagem = new Map<string, number>();
    this.ugs.filter(u => this.categoria(u.nome) === 'coordenacao').forEach(u => {
      const campus = campusDaCoordenacao(u.nome);
      contagem.set(campus, (contagem.get(campus) ?? 0) + 1);
    });
    return [...contagem.entries()]
      .sort((a, b) => b[1] - a[1])
      .map(([valor, total]) => ({ valor, rotulo: valor, total }));
  }

  trackByCampus(index: number, campus: { valor: string }): string {
    return campus.valor;
  }

  selecionarCampus(campus: string): void {
    this.campusAtivo = campus;
    this.aplicarFiltro();
  }

  selecionarFiltro(filtro: FiltroUnidade): void {
    this.filtroAtivo = filtro;
    this.campusAtivo = 'todos';
    this.aplicarFiltro();
  }

  private aplicarFiltro(): void {
    this.campi = this.calcularCampi();
    let grupo = this.filtroAtivo === 'todos'
      ? this.unidadesPrincipais
      : this.ugs.filter(u => this.categoria(u.nome) === this.filtroAtivo);
    if (this.filtroAtivo === 'coordenacao' && this.campusAtivo !== 'todos') {
      grupo = grupo.filter(u => campusDaCoordenacao(u.nome) === this.campusAtivo);
    }

    // A posição vale dentro do que está na tela: sem isso a lista sem coordenações mostrava
    // a posição do ranking geral (ex.: #8 para a unidade com maior execução).
    const anteriorNoGrupo = new Map(
      [...grupo].sort((a, b) => a.posicaoAnterior - b.posicaoAnterior).map((u, i) => [u.id, i + 1] as [string, number])
    );
    this.ugsExibidas = grupo.map((u, i) => {
      const posicao = i + 1;
      const posicaoAnterior = anteriorNoGrupo.get(u.id) ?? posicao;
      const variacaoPosicao = posicaoAnterior - posicao;
      return { ...u, posicao, posicaoAnterior, variacaoPosicao, subiu: variacaoPosicao > 0, caiu: variacaoPosicao < 0 };
    });
    this.atualizarEchartsOptions();
    this.atualizarInsights();
  }

  private atualizarInsights(): void {
    const lista = this.ugsExibidas;
    const insights: InsightIaItem[] = [];

    // Entre as empatadas na menor execução, a de mais ações: é a comparação mais informativa e não depende da ordem alfabética.
    const pior = lista.length ? lista[lista.length - 1].percentual : 0;
    const menor = lista.filter(u => u.percentual === pior).sort((a, b) => b.totalAcoes - a.totalAcoes)[0];

    if (menor) {
      const empatadas = lista.filter(u => u.percentual === menor.percentual).length - 1;
      insights.push({
        icone: '🎯',
        titulo: `Diagnóstico da ${menor.sigla}`,
        prompt: promptDiagnostico(menor.nome),
        badge: empatadas ? `Menor execução (empatada com ${empatadas})` : 'Menor execução'
      });
    }

    if (lista.length >= 2) {
      const maior = lista[0];
      insights.push({
        icone: '📊',
        titulo: `${maior.sigla} x ${menor.sigla}`,
        prompt: promptMaiorXMenor(maior.nome, menor.nome),
        badge: 'Comparativo'
      });
    }

    insights.push(
      {
        icone: '⚠️',
        titulo: 'Ações sem execução',
        prompt: promptAcoesSemExecucao(),
        badge: 'Risco'
      },
      {
        icone: '📄',
        titulo: 'Resumo executivo',
        prompt: promptResumoExecutivo(),
        badge: 'Resumo'
      }
    );

    this.insightsSugeridos = insights;
  }

  // O endpoint não devolve categoria: o grupo vem do prefixo do nome do departamento.
  private categoria(nome: string): Exclude<FiltroUnidade, 'todos'> {
    const n = nome.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    if (n.startsWith('campus')) return 'campus';
    if (n.startsWith('pro-reitoria')) return 'pro-reitoria';
    if (n.startsWith('superintendencia')) return 'superintendencia';
    if (n.startsWith('coordena')) return 'coordenacao';
    return 'outros';
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
    this.selecionarUg.emit(promptDiagnostico(ug.nome));
  }

  clicarInsight(insight: InsightIaItem): void {
    this.selecionarUg.emit(insight.prompt);
  }

  get alturaGrafico(): number {
    const porLinha = this.filtroAtivo === 'coordenacao' ? 40 : 30;
    return Math.max(380, this.ugsExibidas.length * porLinha + 70);
  }

  private atualizarEchartsOptions(): void {
    const cores = lerCoresDoTema(this.host.nativeElement);
    const sorted = [...this.ugsExibidas].reverse();
    const coordenacoes = this.filtroAtivo === 'coordenacao';
    const categorias = sorted.map(u => coordenacoes ? nomeCurtoCoordenacao(u.nome, this.campusAtivo === 'todos') : u.sigla);
    const valores = sorted.map(u => u.percentual);

    this.echartsOptions = {
      animationDuration: 1000,
      animationDurationUpdate: 1000,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        confine: true,
        formatter: (params: any) => {
          const item = Array.isArray(params) ? params[0] : params;
          const ug = sorted[item.dataIndex];
          return `<strong>${ug ? ug.nome : item.name}</strong><br>${item.value}% de execução`;
        }
      },
      grid: {
        top: 20,
        bottom: 30,
        left: 12,
        right: 48,
        containLabel: true
      },
      xAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%', color: cores.textoSuave },
        splitLine: { lineStyle: { color: cores.borda } }
      },
      yAxis: {
        type: 'category',
        data: categorias,
        inverse: true,
        axisLabel: coordenacoes
          ? { color: cores.texto, fontWeight: 'bold', interval: 0, width: 300, overflow: 'break', lineHeight: 14 }
          : { color: cores.texto, fontWeight: 'bold', interval: 0, width: 170, overflow: 'truncate' }
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
            color: cores.texto,
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
