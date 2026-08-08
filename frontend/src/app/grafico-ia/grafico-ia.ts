import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import type { ECharts, EChartsOption } from 'echarts';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';

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
  mostrarValores = true;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['spec'] && this.spec) {
      this.mapearContratoParaECharts();
    }
  }

  onChartInit(chart: ECharts): void {
    this.chartInstance = chart;
  }

  alternarValores(): void {
    this.mostrarValores = !this.mostrarValores;
    this.mapearContratoParaECharts();
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
        },
        label: {
          show: this.mostrarValores,
          position: 'top',
          fontSize: 10
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
          rotate: 0,
          fontSize: 10,
          align: 'center',
          formatter: (valor: string) => this.quebrarRotulo(valor)
        }
      },
      yAxis: { type: 'value' },
      series
    };
  }

  private quebrarRotulo(texto: string): string {
    if (!texto || texto.length <= 14) return texto;

    const palavras = texto.split(' ');
    let linha1 = '';
    let linha2 = '';

    for (const palavra of palavras) {
      if ((linha1 + ' ' + palavra).trim().length <= texto.length / 2 || !linha1) {
        linha1 = (linha1 + ' ' + palavra).trim();
      } else {
        linha2 = (linha2 + ' ' + palavra).trim();
      }
    }

    return linha2 ? `${linha1}\n${linha2}` : linha1;
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
