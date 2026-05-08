// ingestao-ia.ts
import { Component } from '@angular/core';
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
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './ingestao-ia.html',
  styleUrls: ['./ingestao-ia.css']
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

  equipes = [
    'COPLAN',
    'Orçamento',
    'Desenvolvimento',
    'Comunicação',
    'Governança'
  ];

  paineis = [
    'Painel do PAT',
    'Painel Estratégico',
    'Painel de Riscos',
    'Dashboard PROAP'
  ];

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
    const maxSize = 50 * 1024 * 1024; // 50 MB

    for (let i = 0; i < files.length; i++) {
      const arquivo = files[i];

      if (arquivo.size > maxSize) {
        this.mostrarMensagem(`Arquivo ${arquivo.name} excede o limite de 50MB`, true);
        continue;
      }

      const extensao = arquivo.name.split('.').pop()?.toLowerCase();
      const allowed = ['xlsx', 'csv', 'docx', 'pdf'];
      if (!extensao || !allowed.includes(extensao)) {
        this.mostrarMensagem(`Formato não permitido: ${arquivo.name}`, true);
        continue;
      }

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
      this.mostrarMensagem('Selecione ao menos um documento para ingestão.', true);
      return;
    }

    this.carregando = true;
    this.erro = false;

    const processados: number[] = [];

    this.arquivos.forEach((item, index) => {
      if (item.status === 'PROCESSADO') {
        processados.push(index);
        return;
      }

      item.status = 'PROCESSANDO';
      let progresso = 0;

      const intervalo = setInterval(() => {
        progresso += 12 + Math.floor(Math.random() * 8);
        item.progresso = Math.min(progresso, 100);

        if (progresso >= 100) {
          clearInterval(intervalo);
          item.status = 'PROCESSADO';
          item.progresso = 100;
          processados.push(index);

          if (processados.length === this.arquivos.length) {
            this.carregando = false;
            this.mostrarMensagem(
              `${this.arquivos.length} documento(s) enviado(s) para a fila de ingestão da IA.`,
              false
            );
          }
        }
      }, 200 + (index * 80));
    });

    if (this.arquivos.every(a => a.status === 'PROCESSADO')) {
      this.carregando = false;
      this.mostrarMensagem('Documentos já processados anteriormente.', false);
    }
  }

  getQuantidadeArquivos(): number {
    return this.arquivos.length;
  }

  getArquivosProcessados(): number {
    return this.arquivos.filter(arquivo => arquivo.status === 'PROCESSADO').length;
  }

  getTamanhoFormatado(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getIconeArquivo(nome: string): string {
    const ext = nome.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'xlsx': return '📊';
      case 'csv': return '📈';
      case 'pdf': return '📄';
      case 'docx': return '📝';
      default: return '📁';
    }
  }

  getTipoArquivo(nome: string): string {
    const ext = nome.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'xlsx': return 'Planilha Excel';
      case 'csv': return 'CSV';
      case 'pdf': return 'PDF';
      case 'docx': return 'Word';
      default: return 'Documento';
    }
  }

  getStatusTexto(status: string): string {
    switch (status) {
      case 'AGUARDANDO': return 'Na fila';
      case 'PROCESSANDO': return 'Processando';
      case 'PROCESSADO': return 'Processado';
      case 'ERRO': return 'Erro';
      default: return status;
    }
  }

  private mostrarMensagem(texto: string, isErro: boolean): void {
    this.mensagem = texto;
    this.erro = isErro;

    if (this.timeoutMensagem) {
      clearTimeout(this.timeoutMensagem);
    }

    this.timeoutMensagem = setTimeout(() => {
      this.fecharMensagem();
    }, 5000);
  }

  fecharMensagem(): void {
    this.mensagem = '';
    this.erro = false;
    if (this.timeoutMensagem) {
      clearTimeout(this.timeoutMensagem);
    }
  }
}
