import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import type { ECharts, EChartsOption } from 'echarts';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';

const PALETA_POWER_BI = ['#0078D7', '#2CA58D', '#F2A541', '#D9534F', '#8E7CC3', '#5BC0DE'];

@Component({
  selector: 'app-grafico-ia',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  templateUrl: './grafico-ia.html',
  styleUrls: ['./grafico-ia.css'],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })]
})
export class GraficoIaComponent implements OnChanges, OnDestroy {
  @Input() spec: any;
  // Modo prévia pequena (ex: cards de Meus Painéis, onde o card tem altura fixa igual aos do
  // Power BI). Nesse modo: o gráfico preenche 100% do container do pai em vez de calcular a
  // própria altura; a moldura própria (fundo/borda/sombra) some, porque o card de fora já dá essa
  // moldura; título interno e legenda somem (o card de fora já mostra o título, ler pelo card é o
  // objetivo, não pelo gráfico); rótulos de categoria somem (não cabe texto legível num card
  // pequeno com várias categorias); e só o botão de expandir fica na barra de ícones.
  @Input() modoPreview = false;
  chartOptions: EChartsOption = {};
  chartInstance?: ECharts;
  mostrarValores = true;
  chartHeight = 320;
  expandido = false;

  // Usados só pra "devolver" o elemento pro lugar de onde ele veio quando sai da tela cheia.
  private paiOriginal: Node | null = null;
  private irmaoOriginal: Node | null = null;

  // Simplificações do modoPreview só valem enquanto o card está pequeno — expandido, o usuário
  // quer ver tudo (título, legenda, rótulos, números), então "compacto" desliga nesse caso.
  private get compacto(): boolean {
    return this.modoPreview && !this.expandido;
  }

  constructor(private el: ElementRef<HTMLElement>) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['spec'] && this.spec) {
      this.mapearContratoParaECharts();
    }
  }

  ngOnDestroy(): void {
    // Se o componente for destruído enquanto está teleportado pro <body> (ex: usuário troca de
    // sessão de chat com o gráfico expandido), evita deixar um nó órfão preso no body.
    if (this.paiOriginal) {
      this.el.nativeElement.remove();
    }
  }

  onChartInit(chart: ECharts): void {
    this.chartInstance = chart;
  }

  alternarValores(): void {
    this.mostrarValores = !this.mostrarValores;
    this.mapearContratoParaECharts();
  }

  alternarExpandir(): void {
    this.expandido = !this.expandido;
    // Em modoPreview, expandir/fechar liga e desliga as simplificações (título, legenda, rótulos,
    // números) — precisa recalcular as opções, não só redimensionar.
    if (this.modoPreview) {
      this.mapearContratoParaECharts();
    }
    const host = this.el.nativeElement;

    if (this.expandido) {
      // "Teleporta" o elemento inteiro pra fora da árvore do chat, direto pro <body>. O card fica
      // preso lá dentro de algum ancestral que quebra o "position: fixed" (algum elemento no meio
      // do caminho vira um novo "containing block" — geralmente por causa de transform/filter/
      // backdrop-filter/contain — e o fixed passa a se posicionar relativo a ELE, não à tela toda).
      // Mover o nó de verdade resolve isso sem precisar caçar qual ancestral é o culpado.
      this.paiOriginal = host.parentNode;
      this.irmaoOriginal = host.nextSibling;
      document.body.appendChild(host);
    } else if (this.paiOriginal) {
      if (this.irmaoOriginal && this.irmaoOriginal.parentNode === this.paiOriginal) {
        this.paiOriginal.insertBefore(host, this.irmaoOriginal);
      } else {
        this.paiOriginal.appendChild(host);
      }
      this.paiOriginal = null;
      this.irmaoOriginal = null;
    }

    // O ECharts precisa ser avisado manualmente do novo tamanho do container — o canvas dele fica
    // com o tamanho antigo (esticado/cortado) se não for. Dois requestAnimationFrame garantem que
    // o navegador já terminou de aplicar o novo layout antes de medir; o dispatchEvent é reforço
    // pra qualquer listener de resize que o ngx-echarts já tenha registrado sozinho.
    requestAnimationFrame(() => requestAnimationFrame(() => {
      this.chartInstance?.resize();
      window.dispatchEvent(new Event('resize'));
    }));
  }

  @HostListener('document:keydown.escape')
  fecharComEsc(): void {
    if (this.expandido) {
      this.expandido = false;
    }
  }

  baixarPng(): void {
    if (!this.chartInstance) return;

    const url = this.chartInstance.getDataURL({
      type: 'png',
      pixelRatio: 3,
      backgroundColor: '#ffffff'
    });

    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.nomeArquivoBase()}.png`;
    link.click();
  }

  private mapearContratoParaECharts(): void {
    const eixoX = Array.isArray(this.spec?.eixoX) ? this.spec.eixoX : [];
    const rawSeries = Array.isArray(this.spec?.series) ? this.spec.series : [];
    const tipo = this.spec?.tipo || 'bar';

    // Muitas categorias OU nomes compridos (nome de departamento institucional raramente é curto,
    // mesmo com só 4-5 categorias já sobrepõe) espremidos na horizontal viram ilegíveis — Power BI
    // resolve isso virando o gráfico de barras deitado, categoria no eixo vertical. Só faz sentido
    // pra 'bar' (pizza e linha não têm esse problema de rótulo).
    const algumRotuloLongo = eixoX.some((r: string) => String(r ?? '').length > 14);
    const horizontal = tipo === 'bar' && (eixoX.length > 3 || algumRotuloLongo);

    const series = rawSeries.map((s: any, i: number) => {
      const valores = Array.isArray(s.valores) ? s.valores : [];

      // Coerce valores para Number quando possível, mantendo outros tipos como fallback
      const coerced = valores.map((v: any) => {
        if (v === null || v === undefined || v === '') return null;
        if (typeof v === 'number') return v;
        const cleaned = String(v).replace(/[^0-9eE+\-\.]/g, '');
        const n = Number(cleaned);
        return Number.isFinite(n) ? n : null;
      });

      return {
        name: s.nome,
        type: tipo,
        data: coerced,
        smooth: tipo === 'line',
        itemStyle: {
          borderRadius: tipo === 'bar' ? (horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]) : 0,
          color: tipo === 'pie' ? undefined : PALETA_POWER_BI[i % PALETA_POWER_BI.length]
        },
        label: {
          show: this.compacto ? false : this.mostrarValores,
          position: horizontal ? 'right' : 'top',
          fontSize: 10
        }
      };
    });

    const eixoCategoria = {
      type: 'category' as const,
      data: eixoX,
      inverse: horizontal,
      axisLine: { lineStyle: { color: '#d9d9d9' } },
      // Prévia pequena: sem rótulo nenhum — não cabe texto legível pra várias categorias num card
      // de ~170px, e o objetivo ali é só dar uma noção visual (detalhe de verdade é no expandir).
      axisLabel: this.compacto ? { show: false } : {
        interval: 0,
        rotate: 0,
        fontSize: 10,
        align: horizontal ? ('right' as const) : ('center' as const),
        formatter: (valor: string) => this.quebrarRotulo(valor, horizontal ? 30 : 14)
      }
    };

    const eixoValor = {
      type: 'value' as const,
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { color: '#eef1f4', type: 'dashed' as const } }
    };

    // Barras deitadas precisam de mais altura conforme o número de categorias — senão as barras
    // ficam achatadas umas em cima das outras.
    this.chartHeight = horizontal ? Math.max(280, 70 + eixoX.length * 36) : 320;

    // Se não houver dados válidos, monta uma opção mínima para mostrar mensagem no tooltip/legenda
    this.chartOptions = {
      color: PALETA_POWER_BI,
      title: this.compacto ? undefined : {
        text: this.spec?.titulo || '',
        left: 'center',
        textStyle: { fontFamily: 'sans-serif', color: '#333', fontWeight: 600, fontSize: 14 },
        padding: [0, 8, 0, 8]
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: this.compacto ? undefined : { bottom: 0, textStyle: { fontSize: 10 } },
      grid: {
        // Sem rótulo de categoria, sem legenda e sem título interno, o gráfico pode usar quase
        // todo o espaço do card.
        left: this.compacto ? 4 : (horizontal ? 12 : 8),
        right: this.compacto ? 8 : (horizontal ? 40 : 8),
        top: this.compacto ? 6 : 48,
        bottom: this.compacto ? 4 : (horizontal ? 16 : 58),
        containLabel: true
      },
      xAxis: horizontal ? eixoValor : eixoCategoria,
      yAxis: horizontal ? eixoCategoria : eixoValor,
      series
    };
  }

  // Quebra por PALAVRA respeitando um limite de caracteres por linha (em vez de partir o texto
  // sempre bem no meio) — evita deixar um pedacinho solto tipo "Coordenação do Curso - de
  // Matemática Arraias", que ficava ilegível quando o nome tinha uma palavra grande no meio.
  private quebrarRotulo(texto: string, maxPorLinha: number): string {
    const valor = String(texto ?? '');
    if (valor.length <= maxPorLinha) return valor;

    const palavras = valor.split(' ');
    const linhas: string[] = [];
    let atual = '';

    for (const palavra of palavras) {
      const tentativa = atual ? `${atual} ${palavra}` : palavra;
      if (tentativa.length > maxPorLinha && atual) {
        linhas.push(atual);
        atual = palavra;
      } else {
        atual = tentativa;
      }
    }
    if (atual) linhas.push(atual);

    // No máximo 2 linhas — pra nome muito comprido, o resto some, mas isso já era o comportamento
    // anterior; evitar 3+ linhas espremendo o eixo é mais importante que mostrar o nome inteiro.
    return linhas.slice(0, 2).join('\n');
  }

  private nomeArquivoBase(): string {
    return String(this.spec?.titulo || 'grafico-ia')
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase();
  }
}
