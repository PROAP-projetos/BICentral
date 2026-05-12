// ingestao-ia.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface ArquivoUpload {
  arquivo: File;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
  progresso: number;
}

@Component({
  selector: 'app-ingestao-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './ingestao-ia.html',
  styleUrls: ['./ingestao-ia.css']
})
export class IngestaoIaComponent {
  arquivos: ArquivoUpload[] = [];
  equipeSelecionada = 'COPLAN';
  visibilidade = 'PRIVADO';
  usuarioLogado = 'dallyla.moraes';
  carregando = false;
  mensagem = '';
  erro = false;
  timeoutMensagem: any = null;
  agentBubbleVisible = false;
  private agentBubbleTimer?: number;

  equipes = ['COPLAN', 'Orçamento', 'Desenvolvimento', 'Comunicação'];

  constructor() {
    const userRaw = localStorage.getItem('user');
    if (!userRaw) return;

    try {
      const user = JSON.parse(userRaw);
      this.usuarioLogado = user.username || user.email || this.usuarioLogado;
    } catch {
      this.usuarioLogado = 'dallyla.moraes';
    }
  }

  onArquivosSelecionados(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.adicionarArquivos(input.files);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.adicionarArquivos(files);
    }
  }

  private adicionarArquivos(files: FileList): void {
    for (let i = 0; i < files.length; i++) {
      const arquivo = files[i];
      this.arquivos.push({
        arquivo,
        status: 'AGUARDANDO',
        progresso: 0
      });
    }
  }

  removerArquivo(index: number): void {
    this.arquivos.splice(index, 1);
  }

  mostrarBalaoAgente(): void {
    this.agentBubbleVisible = true;
    if (this.agentBubbleTimer) {
      window.clearTimeout(this.agentBubbleTimer);
    }
    this.agentBubbleTimer = window.setTimeout(() => {
      this.agentBubbleVisible = false;
    }, 2600);
  }

  confirmarIngestao(): void {
    if (this.arquivos.length === 0) {
      this.mostrarMensagem('Selecione ao menos um documento.', true);
      return;
    }

    this.carregando = true;
    let concluidos = 0;

    this.arquivos.forEach((item, index) => {
      if (item.status === 'PROCESSADO') {
        concluidos++;
        if (concluidos === this.arquivos.length) {
          this.carregando = false;
        }
        return;
      }

      item.status = 'PROCESSANDO';
      let progresso = 0;

      const intervalo = setInterval(() => {
        progresso += 15 + Math.random() * 10;
        item.progresso = Math.min(progresso, 100);

        if (progresso >= 100) {
          clearInterval(intervalo);
          item.status = 'PROCESSADO';
          concluidos++;

          if (concluidos === this.arquivos.length) {
            this.carregando = false;
            this.mostrarMensagem(`${this.arquivos.length} documento(s) enviado(s) para ingestão.`, false);
          }
        }
      }, 200 + index * 80);
    });
  }

  getQuantidadeArquivos(): number {
    return this.arquivos.length;
  }

  getTamanhoFormatado(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getTipoArquivo(nome: string): string {
    const ext = nome.split('.').pop()?.toLowerCase();
    const tipos: Record<string, string> = {
      xlsx: 'Planilha Excel',
      csv: 'CSV',
      pdf: 'PDF',
      docx: 'Word'
    };
    return tipos[ext || ''] || 'Documento';
  }

  getTipoClasse(nome: string): string {
    const ext = nome.split('.').pop()?.toLowerCase();
    if (ext === 'xlsx' || ext === 'csv') return 'sheet';
    if (ext === 'pdf') return 'pdf';
    if (ext === 'docx') return 'doc';
    return 'default';
  }

  getTipoAbreviado(nome: string): string {
    const ext = nome.split('.').pop()?.toUpperCase();
    return ext || 'DOC';
  }

  private mostrarMensagem(texto: string, isErro: boolean): void {
    this.mensagem = texto;
    this.erro = isErro;
    if (this.timeoutMensagem) clearTimeout(this.timeoutMensagem);
    this.timeoutMensagem = setTimeout(() => this.fecharMensagem(), 5000);
  }

  fecharMensagem(): void {
    this.mensagem = '';
    this.erro = false;
  }
}
