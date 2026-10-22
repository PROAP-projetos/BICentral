import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { forkJoin } from 'rxjs';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { CompartilhamentoDepartamentos, RankingDepartamento, RankingService } from '../services/ranking.service';
import { aoMudarTema, lerCoresDoTema } from '../shared/tema';
import { extrairSigla } from '../shared/sigla';

const GAP_ATENCAO = 40;
// Mesmo corte do @media do CSS: abaixo disso o layout do grafo encolhe para caber na tela.
const LARGURA_ESTREITA = 700;

@Component({
  selector: 'app-mapa-compartilhamento',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })],
  templateUrl: './mapa-compartilhamento.component.html',
  styleUrls: ['./mapa-compartilhamento.component.css']
})
export class MapaCompartilhamentoComponent implements OnInit, OnDestroy {
  @ViewChild('container') container?: ElementRef<HTMLElement>;

  carregando = false;
  erro = '';
  vazio = false;
  opcoes: any;
  telaCheia = false;

  // Sem a API de tela cheia (alguns celulares), cobre a janela por CSS.
  private telaCheiaPorCss = false;

  // Layout montado para tela estreita ou larga; só remonta quando cruza o corte.
  private montadoEstreito = this.telaEstreita();

  readonly toque = typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches;

  constructor(private rankingService: RankingService, private host: ElementRef<HTMLElement>) {}

  private dados?: { ligacoes: CompartilhamentoDepartamentos[]; ranking: RankingDepartamento[] };
  private pararObservarTema?: () => void;

  ngOnInit(): void {
    this.carregar();
    this.pararObservarTema = aoMudarTema(this.host.nativeElement, () => {
      if (this.dados) this.opcoes = this.montarOpcoes(this.dados.ligacoes, this.dados.ranking);
    });
  }

  ngOnDestroy(): void {
    this.pararObservarTema?.();
  }

  carregar(): void {
    this.carregando = true;
    this.erro = '';

    forkJoin({
      ligacoes: this.rankingService.compartilhamento(),
      ranking: this.rankingService.listarRanking()
    }).subscribe({
      next: ({ ligacoes, ranking }) => {
        this.vazio = ligacoes.length === 0;
        this.dados = { ligacoes, ranking };
        this.opcoes = this.montarOpcoes(ligacoes, ranking);
        this.carregando = false;
      },
      error: () => {
        this.erro = 'Não foi possível carregar o mapa de ações compartilhadas agora.';
        this.carregando = false;
      }
    });
  }

  async alternarTelaCheia(): Promise<void> {
    if (this.telaCheia) {
      await this.sairDaTelaCheia();
      return;
    }

    const elemento = this.container?.nativeElement;
    if (!elemento) return;

    try {
      if (!elemento.requestFullscreen) throw new Error('sem suporte');
      await elemento.requestFullscreen();
      this.telaCheiaPorCss = false;
    } catch {
      this.telaCheiaPorCss = true;
    }
    this.telaCheia = true;
    this.redimensionarGrafico();
  }

  private async sairDaTelaCheia(): Promise<void> {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    }
    this.telaCheiaPorCss = false;
    this.telaCheia = false;
    this.redimensionarGrafico();
  }

  @HostListener('document:fullscreenchange')
  aoMudarTelaCheia(): void {
    if (!document.fullscreenElement && !this.telaCheiaPorCss) {
      this.telaCheia = false;
    }
    this.redimensionarGrafico();
  }

  @HostListener('window:resize')
  aoRedimensionar(): void {
    const estreito = this.telaEstreita();
    if (estreito === this.montadoEstreito || !this.dados) return;
    this.opcoes = this.montarOpcoes(this.dados.ligacoes, this.dados.ranking);
  }

  @HostListener('document:keydown.escape')
  aoApertarEsc(): void {
    if (this.telaCheiaPorCss) {
      this.sairDaTelaCheia();
    }
  }

  get classeTelaCheiaCss(): boolean {
    return this.telaCheia && this.telaCheiaPorCss;
  }

  private redimensionarGrafico(): void {
    setTimeout(() => window.dispatchEvent(new Event('resize')), 60);
  }

  private telaEstreita(): boolean {
    return typeof window !== 'undefined' && window.innerWidth < LARGURA_ESTREITA;
  }

  private montarOpcoes(ligacoes: CompartilhamentoDepartamentos[], ranking: RankingDepartamento[]): any {
    // O layout de força não se ajusta à caixa: com as distâncias do desktop o grafo passa
    // da largura do celular. Forças e pontos menores fazem ele caber.
    const estreito = this.telaEstreita();
    this.montadoEstreito = estreito;
    const escalaPonto = estreito ? 0.65 : 1;
    const execucaoPorDepartamento = new Map(ranking.map(r => [r.departamento, r.mediaExecucaoPct]));
    const cores = this.lerCores();

    const acoesPorNo = new Map<string, number>();
    ligacoes.forEach(l => {
      acoesPorNo.set(l.origem, (acoesPorNo.get(l.origem) ?? 0) + l.qtdAcoes);
      acoesPorNo.set(l.destino, (acoesPorNo.get(l.destino) ?? 0) + l.qtdAcoes);
    });

    const nos = [...acoesPorNo.entries()].map(([departamento, total]) => {
      const execucao = execucaoPorDepartamento.get(departamento);
      return {
        id: departamento,
        name: extrairSigla(departamento),
        departamento,
        execucao,
        acoesCompartilhadas: total,
        symbolSize: Math.min(60, 14 + Math.sqrt(total) * 6) * escalaPonto,
        itemStyle: { color: this.corDoNo(execucao), borderWidth: 0 }
      };
    });

    const arestas = ligacoes.map(l => {
      const destaque = l.diferencaMediaPct >= GAP_ATENCAO;
      return {
        source: l.origem,
        target: l.destino,
        qtdAcoes: l.qtdAcoes,
        diferencaMediaPct: l.diferencaMediaPct,
        lineStyle: {
          width: Math.min(5, 0.8 + l.qtdAcoes / 4),
          color: destaque ? cores.destaque : cores.linha,
          opacity: destaque ? 0.85 : 1,
          curveness: 0.1
        }
      };
    });

    return {
      backgroundColor: 'transparent',
      tooltip: {
        confine: true,
        formatter: (p: any) => {
          if (p.dataType === 'edge') {
            return `${extrairSigla(p.data.source)} ↔ ${extrairSigla(p.data.target)}<br>` +
              `${p.data.qtdAcoes} ação(ões) em comum<br>` +
              `Diferença média de execução: ${p.data.diferencaMediaPct} pp`;
          }
          const execucao = p.data.execucao == null ? 'sem dado' : `${p.data.execucao}% de execução`;
          return `${p.data.departamento}<br>${execucao}<br>${p.data.acoesCompartilhadas} ação(ões) em ligações compartilhadas`;
        }
      },
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        data: nos,
        links: arestas,
        // layoutAnimation: false calcula o layout de uma vez, sem os pontos balançando.
        force: estreito
          ? { repulsion: 110, edgeLength: [25, 80], gravity: 0.25, friction: 0.85, layoutAnimation: false }
          : { repulsion: 420, edgeLength: [60, 190], gravity: 0.08, friction: 0.85, layoutAnimation: false },
        label: { show: true, position: 'bottom', color: cores.texto, fontSize: estreito ? 9 : 11, fontWeight: 500 },
        emphasis: { focus: 'adjacency', lineStyle: { opacity: 1, width: 3 } },
        blur: { itemStyle: { opacity: 0.25 }, lineStyle: { opacity: 0.1 }, label: { opacity: 0.3 } }
      }]
    };
  }

  private lerCores(): { texto: string; linha: string; destaque: string } {
    const tema = lerCoresDoTema(this.host.nativeElement);
    return { texto: tema.texto, linha: 'rgba(148, 163, 184, 0.45)', destaque: '#e5484d' };
  }

  private corDoNo(execucao: number | undefined): string {
    if (execucao == null) return 'rgba(148, 163, 184, 0.6)';
    const intensidade = 0.25 + 0.75 * Math.min(100, Math.max(0, execucao)) / 100;
    return `rgba(37, 99, 235, ${intensidade.toFixed(2)})`;
  }

}
