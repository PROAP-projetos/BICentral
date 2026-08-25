import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { RankingDepartamento, RankingService } from '../services/ranking.service';

export type CategoriaUnidade = 'campus' | 'coordenacao' | 'ug' | 'outro';

export interface UgRankingItem {
  id: string;
  sigla: string;
  nome: string;
  categoria: CategoriaUnidade;
  campus: string | null;
  posicao: number;
  posicaoAnterior: number | null;
  percentual: number;
  qtdAcoes: number;
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

@Component({
  selector: 'app-leaderboard-ug',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })],
  templateUrl: './leaderboard-ug.component.html',
  styleUrls: ['./leaderboard-ug.component.css']
})
export class LeaderboardUgComponent implements OnInit {

  @Output() selecionarUg = new EventEmitter<string>();

  modoVisualizacao: 'cards' | 'echarts' = 'cards';
  carregando = false;
  erro = '';

  ugs: UgRankingItem[] = [];
  filtroCategoria: CategoriaUnidade | 'todos' = 'todos';
  filtroCampus: string | 'todos' = 'todos';
  totalAcoesUnicas = 0;

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
    this.rankingService.buscarResumo().subscribe({
      next: (r) => this.totalAcoesUnicas = r.totalAcoesUnicas,
      error: () => {} // KPI secundário — se falhar, mantém 0 e não bloqueia o resto da tela
    });
  }

  carregarRanking(): void {
    this.carregando = true;
    this.erro = '';

    this.rankingService.listarRanking().subscribe({
      next: (dados) => {
        this.ugs = dados.map((d) => this.mapearParaUgRankingItem(d));
        this.atualizarEchartsOptions();
        this.carregando = false;
      },
      error: () => {
        this.erro = 'Não foi possível carregar o ranking agora.';
        this.carregando = false;
      }
    });
  }

  // Cidades dos campi da UFT — usado pra identificar a qual campus uma coordenação de curso pertence,
  // já que isso não vem como campo separado do backend, só embutido no texto do nome.
  private static readonly CAMPI_CONHECIDOS = ['Palmas', 'Gurupi', 'Arraias', 'Miracema', 'Porto Nacional'];

  private identificarCampus(textoOriginal: string): string | null {
    for (const campus of LeaderboardUgComponent.CAMPI_CONHECIDOS) {
      if (textoOriginal.includes(campus)) return campus;
    }
    return null;
  }

  private mapearParaUgRankingItem(d: RankingDepartamento): UgRankingItem {
    const variacaoPosicao = d.posicaoAnterior != null ? d.posicaoAnterior - d.posicaoAtual : 0;
    const { sigla, nome, categoria } = this.classificarDepartamento(d.departamento, d.tipoUnidade);

    return {
      id: d.departamento,
      sigla,
      nome,
      categoria,
      campus: this.identificarCampus(d.departamento),
      posicao: d.posicaoAtual,
      posicaoAnterior: d.posicaoAnterior,
      percentual: d.mediaExecucaoPct,
      qtdAcoes: d.qtdAcoes,
      variacaoPosicao,
      subiu: variacaoPosicao > 0,
      caiu: variacaoPosicao < 0,
      destaqueAnimacao: variacaoPosicao > 0
    };
  }

  // Prefixos conhecidos de coordenação de curso/programa, do mais específico pro mais genérico —
  // usa o primeiro que bater pra tirar o "rótulo" e sobrar só o nome do curso/programa.
  private static readonly PREFIXOS_COORDENACAO: RegExp[] = [
    /^Coordenação do Curso de\s+/i,
    /^Coordenação do Programa de Pós-graduação em\s+/i,
    /^Coordenação da\s+/i,
    /^Coordenação de\s+/i,
    /^Coord\.?\s+/i, // cobre "COORD POS-GRAD..." e "COORD. CURSO..." (grafia abreviada usada por algumas coordenações)
  ];

  // "GAB" aparece como último segmento em vários nomes só pra indicar vínculo com o Gabinete do
  // Reitor (ex: "Procuradoria Jurídica - PROJUR - GAB") — não é a sigla própria da unidade.
  private static readonly SUFIXOS_VINCULO_CONHECIDOS = new Set(['GAB']);

  // Classifica o departamento em campus / coordenação / UG a partir do padrão real do nome
  // (confirmado nos 105 departamentos existentes hoje no banco) — o backend só distingue
  // UA (acadêmica) de UG (gestora), então campus vs. coordenação precisa vir do texto:
  // - "Coordenação... - Campus"          → o que vem depois do traço é o CAMPUS/vínculo, não sigla.
  // - "Campus Universitário de X - SIGLA" → o que vem depois do traço É a sigla de verdade.
  // - "Nome - SIGLA - GAB" (3+ partes)    → a sigla de verdade fica no MEIO, o último segmento é só
  //                                          o vínculo hierárquico (ex: SITAI, não GAB).
  // - "Nome - GAB" (2 partes, mas GAB)     → GAB não identifica a unidade, usa o nome inteiro.
  // - "Nome - SIGLA" (caso normal)         → o que vem depois do traço É a sigla de verdade.
  // Separador de segmento tolerante a espaçamento inconsistente na fonte (ex: "Saúde- CESAU" sem
  // espaço antes do traço) — exige espaço em pelo menos um dos lados, senão junta palavra composta
  // com hífen (ex: "Clínica-escola") sendo cortada por engano.
  private static readonly SEPARADOR_SEGMENTO = /\s+-\s*|\s*-\s+/;

  private dividirSegmentos(texto: string): string[] {
    return texto.split(LeaderboardUgComponent.SEPARADOR_SEGMENTO).map(p => p.trim()).filter(p => p.length > 0);
  }

  private classificarDepartamento(
    nomeCompleto: string,
    tipoUnidade: 'UA' | 'UG' | null
  ): { sigla: string; nome: string; categoria: CategoriaUnidade } {
    if (/^Coord/i.test(nomeCompleto)) {
      let semPrefixo = nomeCompleto;
      for (const prefixo of LeaderboardUgComponent.PREFIXOS_COORDENACAO) {
        if (prefixo.test(nomeCompleto)) {
          semPrefixo = nomeCompleto.replace(prefixo, '');
          break;
        }
      }
      const partes = this.dividirSegmentos(semPrefixo);
      const curso = partes[0];
      const vinculo = partes.length > 1 ? partes[partes.length - 1] : '';
      return {
        sigla: curso,
        nome: vinculo ? `Coordenação — ${vinculo}` : 'Coordenação de curso',
        categoria: 'coordenacao'
      };
    }

    const partes = this.dividirSegmentos(nomeCompleto);

    if (/^Campus/i.test(nomeCompleto) && partes.length > 1) {
      return {
        sigla: partes[partes.length - 1],
        nome: partes[0],
        categoria: 'campus'
      };
    }

    if (partes.length > 1) {
      const ultimo = partes[partes.length - 1];
      const categoria: CategoriaUnidade = tipoUnidade === 'UG' ? 'ug' : 'outro';

      // 3+ segmentos: sigla de verdade fica no meio, o último é só vínculo hierárquico
      if (partes.length >= 3) {
        return { sigla: partes[1], nome: partes[0], categoria };
      }

      // 2 segmentos, mas o último é um rótulo de vínculo conhecido (não uma sigla própria)
      if (LeaderboardUgComponent.SUFIXOS_VINCULO_CONHECIDOS.has(ultimo.toUpperCase())) {
        return { sigla: partes[0], nome: partes[0], categoria };
      }

      return { sigla: ultimo, nome: partes[0], categoria };
    }

    return {
      sigla: nomeCompleto,
      nome: nomeCompleto,
      categoria: tipoUnidade === 'UG' ? 'ug' : 'outro'
    };
  }

  // Getters para KPIs no topo
  get percentualGlobal(): number {
    if (!this.ugs.length) return 0;
    const soma = this.ugs.reduce((acc, ug) => acc + ug.percentual, 0);
    return Math.round((soma / this.ugs.length) * 10) / 10;
  }

  get totalUgsEmAtencao(): number {
    return this.ugs.filter(u => u.percentual < 60).length;
  }

  get totalCampus(): number {
    return this.ugs.filter(u => u.categoria === 'campus').length;
  }

  get totalCoordenacoes(): number {
    return this.ugs.filter(u => u.categoria === 'coordenacao').length;
  }

  get totalUg(): number {
    return this.ugs.filter(u => u.categoria === 'ug').length;
  }

  get campiComDados(): string[] {
    return LeaderboardUgComponent.CAMPI_CONHECIDOS.filter(c => this.ugs.some(u => u.campus === c));
  }

  get ugsFiltrados(): UgRankingItem[] {
    return this.ugs.filter(u =>
      (this.filtroCategoria === 'todos' || u.categoria === this.filtroCategoria) &&
      (this.filtroCampus === 'todos' || u.campus === this.filtroCampus)
    );
  }

  get echartsWrapHeight(): number {
    return Math.max(380, this.ugsFiltrados.length * 28);
  }

  definirFiltro(categoria: CategoriaUnidade | 'todos'): void {
    this.filtroCategoria = categoria;
    this.atualizarEchartsOptions();
  }

  definirFiltroCampus(campus: string): void {
    this.filtroCampus = campus;
    this.atualizarEchartsOptions();
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

  getCategoriaLabel(categoria: CategoriaUnidade): string {
    switch (categoria) {
      case 'campus': return 'Campus';
      case 'coordenacao': return 'Coordenação de curso';
      case 'ug': return 'Unidade Gestora';
      default: return 'Unidade Acadêmica';
    }
  }

  getClasseCorPct(percentual: number): string {
    if (percentual >= 75) return 'pct-alto';
    if (percentual >= 50) return 'pct-medio';
    return 'pct-baixo';
  }

  clicarUg(ug: UgRankingItem): void {
    this.selecionarUg.emit(`Faça uma análise detalhada do desempenho e metas da ${ug.sigla} (${ug.nome}).`);
  }

  clicarInsight(insight: InsightIaItem): void {
    this.selecionarUg.emit(insight.prompt);
  }

  private atualizarEchartsOptions(): void {
    const sorted = [...this.ugsFiltrados].reverse();
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
        // {b} sozinho é só a sigla curta (ex: "Matemática"), que pode repetir em campus
        // diferentes — mostra o nome completo (com contexto de campus/coordenação) junto.
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const item = sorted[p.dataIndex];
          if (!item) return '';
          return `<strong>${item.sigla}</strong><br/>${item.nome}<br/>${item.percentual}% de execução`;
        }
      },
      grid: {
        top: 20,
        bottom: 30,
        left: 140,
        right: 60
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
        axisLabel: { color: '#f8fafc', fontWeight: 'bold' },
        // espaço entre barras proporcional ao total, senão com muitos departamentos fica tudo colado
        boundaryGap: ['10%', '10%']
      },
      series: [
        {
          name: 'Execução PAT',
          type: 'bar',
          data: valores,
          realtimeSort: true,
          barCategoryGap: '45%',
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
