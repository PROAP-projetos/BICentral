import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import type { ECharts, EChartsOption } from 'echarts';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';

interface LinhaTabelaGrafico {
  categoria: string;
  valores: (string | number)[];
}

@Component({
  selector: 'app-grafico-ia',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  templateUrl: './grafico-ia.html',
  styleUrls: ['./grafico-ia.css'],
  providers: [provideEchartsCore({ echarts: () => import('echarts') })]
})
export class GraficoIaComponent implements OnChanges {
  @Input() spec: any; 
  chartOptions: EChartsOption = {};
  chartInstance?: ECharts;
  mostrarTabela = false;
  mensagemAcao = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['spec'] && this.spec) {
      this.mapearContratoParaECharts();
    }
  }

  onChartInit(chart: ECharts): void {
    this.chartInstance = chart;
  }

  baixarPng(): void {
    if (!this.chartInstance) return;

    const url = this.chartInstance.getDataURL({
      type: 'png',
      pixelRatio: 3,
      backgroundColor: '#ffffff'
    });

    this.baixarArquivo(url, `${this.nomeArquivoBase()}.png`);
  }

  async copiarImagem(): Promise<void> {
    if (!this.chartInstance) return;

    try {
      const url = this.chartInstance.getDataURL({
        type: 'png',
        pixelRatio: 3,
        backgroundColor: '#ffffff'
      });
      const blob = await (await fetch(url)).blob();
      await navigator.clipboard.write([
        new ClipboardItem({ [blob.type]: blob })
      ]);
      this.exibirMensagemAcao('Imagem copiada.');
    } catch {
      this.exibirMensagemAcao('Não foi possível copiar a imagem.');
    }
  }

  baixarCsv(): void {
    const csv = this.gerarCsv();
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    this.baixarArquivo(url, `${this.nomeArquivoBase()}.csv`);
    URL.revokeObjectURL(url);
  }

  async copiarTabela(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.gerarCsv('\t'));
      this.exibirMensagemAcao('Tabela copiada.');
    } catch {
      this.exibirMensagemAcao('Não foi possível copiar a tabela.');
    }
  }

  alternarTabela(): void {
    this.mostrarTabela = !this.mostrarTabela;
  }

  get nomesSeries(): string[] {
    return (this.spec?.series || []).map((serie: any) => serie.nome);
  }

  get linhasTabela(): LinhaTabelaGrafico[] {
    const categorias = this.spec?.eixoX || [];
    const series = this.spec?.series || [];

    return categorias.map((categoria: string, index: number) => ({
      categoria,
      valores: series.map((serie: any) => serie.valores?.[index] ?? '')
    }));
  }

  private mapearContratoParaECharts(): void {
    const eixoX = Array.isArray(this.spec?.eixoX) ? this.spec.eixoX : [];
    const rawSeries = Array.isArray(this.spec?.series) ? this.spec.series : [];

    const series = rawSeries.map((s: any) => {
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
        type: this.spec.tipo || 'bar',
        data: coerced,
        smooth: (this.spec.tipo || 'bar') === 'line',
        itemStyle: {
          borderRadius: this.spec.tipo === 'bar' ? [4, 4, 0, 0] : 0
        }
      };
    });

    // Se não houver dados válidos, monta uma opção mínima para mostrar mensagem no tooltip/legenda
    this.chartOptions = {
      title: {
        text: this.spec?.titulo || '',
        left: 'center',
        textStyle: { fontFamily: 'sans-serif', color: '#333', fontSize: 14 },
        padding: [0, 8, 0, 8]
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: { bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: 8, right: 8, top: 48, bottom: 58, containLabel: true },
      xAxis: {
        type: 'category',
        data: eixoX,
        axisLabel: {
          interval: 0,
          rotate: 35,
          fontSize: 10
        }
      },
      yAxis: { type: 'value' },
      series
    };
  }

  private gerarCsv(separador = ','): string {
    const escapar = (valor: string | number) => {
      const texto = String(valor ?? '');
      if (separador === '\t') return texto;
      return `"${texto.replace(/"/g, '""')}"`;
    };

    const cabecalho = ['Categoria', ...this.nomesSeries].map(escapar).join(separador);
    const linhas = this.linhasTabela.map((linha) => [
      escapar(linha.categoria),
      ...linha.valores.map(escapar)
    ].join(separador));

    return [cabecalho, ...linhas].join('\n');
  }

  private baixarArquivo(url: string, nomeArquivo: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = nomeArquivo;
    link.click();
  }

  private nomeArquivoBase(): string {
    return String(this.spec?.titulo || 'grafico-ia')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase();
  }

  private exibirMensagemAcao(mensagem: string): void {
    this.mensagemAcao = mensagem;
    window.setTimeout(() => {
      this.mensagemAcao = '';
    }, 2500);
  }
}
