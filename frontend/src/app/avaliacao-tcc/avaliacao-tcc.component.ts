import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AvaliacaoTccService, ItemAvaliacao, NovoItemAvaliacao, TempoManual } from '../services/avaliacao-tcc.service';

interface ItemNaTela extends ItemAvaliacao {
  salvo: boolean;
  salvando: boolean;
  erro: string;
  respostaHtml: string;
  referenciaHtml: string;
  perguntaCurta: string;
  perguntaLonga: boolean;
  // Replay em formato de chat: a pergunta aparece, o proIAp "digita" e então a resposta gravada na rodada.
  mostrado: boolean;
  respondido: boolean;
  mostrarComentario: boolean;
}

// Perguntas vindas dos botões do painel carregam o roteiro inteiro de resposta; no balão basta a primeira frase.
const LIMITE_PERGUNTA = 160;
const ATRASO_DIGITANDO_MS = 1100;

@Component({
  selector: 'app-avaliacao-tcc',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './avaliacao-tcc.component.html',
  styleUrls: ['../painel-admin/painel-admin.component.css', './avaliacao-tcc.component.css']
})
export class AvaliacaoTccComponent implements OnInit, OnDestroy {
  itens: ItemNaTela[] = [];
  souAdmin = false;
  carregando = true;
  erroCarregar = '';
  digitando = false;
  mensagemAdmin = '';

  readonly opcoesTempo: { valor: TempoManual; rotulo: string }[] = [
    { valor: '<5', rotulo: 'Menos de 5 min' },
    { valor: '5-15', rotulo: '5 a 15 min' },
    { valor: '15-60', rotulo: '15 a 60 min' },
    { valor: '>60', rotulo: 'Mais de 1 hora' },
    { valor: 'pedir', rotulo: 'Precisaria pedir a alguém' }
  ];

  private timers: number[] = [];

  constructor(private service: AvaliacaoTccService) {}

  ngOnInit(): void {
    this.carregar();
  }

  ngOnDestroy(): void {
    this.timers.forEach(t => window.clearTimeout(t));
  }

  get concluidos(): number {
    return this.itens.filter(i => i.salvo).length;
  }

  get terminou(): boolean {
    return this.itens.length > 0 && this.concluidos === this.itens.length;
  }

  get progressoPct(): number {
    return this.itens.length ? Math.round((this.concluidos / this.itens.length) * 100) : 0;
  }

  carregar(): void {
    this.carregando = true;
    this.service.listar().subscribe({
      next: (r) => {
        this.souAdmin = r.souAdmin;
        this.itens = r.itens.map(i => {
          const salvo = i.correta !== null && i.completa !== null;
          return {
            ...i,
            salvo,
            salvando: false,
            erro: '',
            respostaHtml: this.markdownParaHtml(i.resposta),
            referenciaHtml: this.markdownParaHtml(i.referencia),
            perguntaCurta: this.primeiraFrase(i.pergunta),
            perguntaLonga: i.pergunta.length > LIMITE_PERGUNTA,
            // Quem volta depois vê o que já avaliou como histórico da conversa.
            mostrado: salvo,
            respondido: salvo,
            mostrarComentario: !!i.comentario
          };
        });
        this.carregando = false;
        this.revelarProximo();
      },
      error: () => {
        this.erroCarregar = 'Não foi possível carregar a avaliação. Recarregue a página ou faça login de novo.';
        this.carregando = false;
      }
    });
  }

  responder(item: ItemNaTela, campo: 'correta' | 'completa', valor: boolean): void {
    if (item.salvando) return;
    item[campo] = valor;
    if (item.correta !== null && item.completa !== null) {
      this.salvar(item);
    }
  }

  // Opcional: grava junto se as duas marcações já existem; senão vai no salvamento delas.
  marcarTempo(item: ItemNaTela, valor: TempoManual): void {
    if (item.salvando) return;
    item.tempoManual = item.tempoManual === valor ? null : valor;
    if (item.correta !== null && item.completa !== null) {
      this.salvar(item);
    }
  }

  salvarComentario(item: ItemNaTela): void {
    if (item.correta !== null && item.completa !== null) {
      this.salvar(item);
    }
  }

  dica(item: ItemNaTela): string {
    if (item.correta === null && item.completa === null) return 'Marque as duas perguntas acima para seguir para a próxima.';
    if (item.correta === null) return 'Falta marcar se a resposta está certa.';
    if (item.completa === null) return 'Falta marcar se a resposta está completa.';
    return '';
  }

  // Avanço manual: aparece só no último cartão exibido, já salvo, quando ainda há pergunta por vir.
  podeIrParaProxima(item: ItemNaTela): boolean {
    if (!item.salvo || this.digitando || this.terminou) return false;
    const ultimoMostrado = [...this.itens].reverse().find(i => i.mostrado);
    return ultimoMostrado === item && this.itens.some(i => !i.mostrado);
  }

  proxima(): void {
    this.revelarProximo();
  }

  trackById(_: number, item: ItemNaTela): string {
    return item.id;
  }

  private salvar(item: ItemNaTela): void {
    item.salvando = true;
    item.erro = '';
    this.service.salvar(item.id, {
      correta: item.correta,
      completa: item.completa,
      tempoManual: item.tempoManual,
      comentario: item.comentario
    }).subscribe({
      next: () => {
        const primeiraVez = !item.salvo;
        item.salvo = true;
        item.salvando = false;
        if (primeiraVez) {
          this.revelarProximo();
        }
      },
      error: () => {
        item.salvando = false;
        item.erro = 'Não salvou. Confira a conexão e marque de novo.';
      }
    });
  }

  // Mostra a próxima pergunta pendente ainda não exibida, com o "digitando" antes da resposta.
  private revelarProximo(): void {
    if (this.digitando || this.itens.some(i => i.mostrado && !i.salvo)) return;
    const proximo = this.itens.find(i => !i.mostrado);
    if (!proximo) {
      this.rolarPara('fim-avaliacao');
      return;
    }
    const semAnimacao = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    this.agendar(() => {
      proximo.mostrado = true;
      this.digitando = true;
      this.rolarPara('msg-' + proximo.id);
      this.agendar(() => {
        this.digitando = false;
        proximo.respondido = true;
        this.rolarPara('msg-' + proximo.id);
      }, semAnimacao ? 0 : ATRASO_DIGITANDO_MS);
    }, semAnimacao ? 0 : 350);
  }

  private agendar(fn: () => void, ms: number): void {
    this.timers.push(window.setTimeout(fn, ms));
  }

  private rolarPara(id: string): void {
    this.agendar(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
  }

  importarArquivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    if (!arquivo) return;
    arquivo.text().then(texto => {
      let itens: NovoItemAvaliacao[];
      try {
        itens = JSON.parse(texto);
      } catch {
        this.mensagemAdmin = 'O arquivo não é um JSON válido.';
        return;
      }
      this.service.importar(itens).subscribe({
        next: (r) => {
          this.mensagemAdmin = `${r.importados} item(ns) importado(s).`;
          input.value = '';
          this.carregar();
        },
        error: (e) => (this.mensagemAdmin = e?.error?.message || 'Falha ao importar os itens.')
      });
    });
  }

  exportarResultado(): void {
    this.service.resultado().subscribe({
      next: (linhas) => {
        const colunas = ['item', 'tipo', 'usuarioId', 'avaliador', 'correta', 'completa', 'tempoManual', 'comentario', 'atualizadoEm'];
        const celula = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
        const csv = [colunas.join(';'), ...linhas.map(l => colunas.map(c => celula(l[c])).join(';'))].join('\r\n');
        const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = 'avaliacao-tcc-resultado.csv';
        link.click();
        URL.revokeObjectURL(url);
        this.mensagemAdmin = `${linhas.length} avaliação(ões) exportada(s).`;
      },
      error: () => (this.mensagemAdmin = 'Falha ao exportar o resultado.')
    });
  }

  private primeiraFrase(texto: string): string {
    if (texto.length <= LIMITE_PERGUNTA) return texto;
    const fim = texto.search(/[.?!]\s/);
    return fim > 0 && fim < LIMITE_PERGUNTA * 2 ? texto.slice(0, fim + 1) : texto.slice(0, LIMITE_PERGUNTA).trimEnd() + '…';
  }

  // As respostas do agente vêm em markdown (tabelas, negrito, listas). Escapa o HTML antes de
  // montar as tags, então o [innerHTML] só recebe marcação gerada aqui.
  private markdownParaHtml(texto: string): string {
    const escapar = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const inline = (s: string) => escapar(s)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');

    const linhas = (texto || '').replace(/<br\s*\/?>/gi, '\n').split('\n');
    const html: string[] = [];
    let i = 0;
    while (i < linhas.length) {
      const linha = linhas[i].trim();
      if (linha.startsWith('|')) {
        const bloco: string[] = [];
        while (i < linhas.length && linhas[i].trim().startsWith('|')) {
          bloco.push(linhas[i].trim());
          i++;
        }
        const celulas = (l: string) => l.replace(/^\||\|$/g, '').split('|').map(c => c.trim());
        const corpo = bloco.filter(l => !/^\|?\s*:?-{2,}/.test(l));
        const [cabecalho, ...resto] = corpo;
        html.push('<div class="tabela-wrap"><table><thead><tr>'
          + (cabecalho ? celulas(cabecalho).map(c => `<th>${inline(c)}</th>`).join('') : '')
          + '</tr></thead><tbody>'
          + resto.map(l => '<tr>' + celulas(l).map(c => `<td>${inline(c)}</td>`).join('') + '</tr>').join('')
          + '</tbody></table></div>');
        continue;
      }
      if (/^[-*] /.test(linha)) {
        const itens: string[] = [];
        while (i < linhas.length && /^[-*] /.test(linhas[i].trim())) {
          itens.push(`<li>${inline(linhas[i].trim().slice(2))}</li>`);
          i++;
        }
        html.push(`<ul>${itens.join('')}</ul>`);
        continue;
      }
      if (/^#{1,6} /.test(linha)) {
        html.push(`<h4>${inline(linha.replace(/^#+ /, ''))}</h4>`);
      } else if (linha && !linha.startsWith('```')) {
        html.push(`<p>${inline(linha)}</p>`);
      }
      i++;
    }
    return html.join('');
  }
}
