// ingestao-ia.ts
import { Component, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface ArquivoUpload {
  arquivo: File;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
  progresso: number;
}

@Component({
  selector: 'app-ingestao-ia',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ingestao-ia.html',
  styleUrls: ['./ingestao-ia.css'],
  encapsulation: ViewEncapsulation.None
})
export class IngestaoIaComponent {
  arquivos: ArquivoUpload[] = [];
  equipeSelecionada = 'COPLAN';
  painelRelacionado = 'Painel do PAT';
  visibilidade = 'PRIVADO';
  carregando = false;
  mensagem = '';
  erro = false;
  timeoutMensagem: any = null;

  equipes = ['COPLAN', 'Orçamento', 'Desenvolvimento', 'Comunicação'];
  paineis = ['Painel do PAT', 'Painel Estratégico', 'Painel de Riscos'];

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
