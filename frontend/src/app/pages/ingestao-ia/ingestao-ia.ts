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
  styleUrl: './ingestao-ia.css',
})
export class IngestaoIaComponent {

  arquivos: ArquivoUpload[] = [];

  equipeSelecionada = 'COMUNICACAO';

  visibilidade = 'PUBLICO';

  carregando = false;

  mensagem = '';

  erro = false;

  equipes = [
    'COPLAN',
    'Orçamento',
    'Desenvolvimento'
  ];

  onArquivosSelecionados(event: Event): void {

    const input = event.target as HTMLInputElement;

    if (!input.files) return;

    for (let i = 0; i < input.files.length; i++) {

      const arquivo = input.files[i];

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

      this.erro = true;

      this.mensagem = 'Selecione ao menos um documento para ingestão.';

      return;
    }

    this.carregando = true;

    this.erro = false;

    this.mensagem = '';

    this.arquivos.forEach((item, index) => {

      item.status = 'PROCESSANDO';

      let progresso = 0;

      const intervalo = setInterval(() => {

        progresso += 10;

        item.progresso = progresso;

        if (progresso >= 100) {

          clearInterval(intervalo);

          item.status = 'PROCESSADO';

          item.progresso = 100;

          const todosFinalizados = this.arquivos.every(
            arquivo => arquivo.status === 'PROCESSADO'
          );

          if (todosFinalizados) {

            this.carregando = false;

            this.mensagem =
              'Todos os documentos foram enviados para a fila de ingestão da IA.';
          }
        }

      }, 250 + (index * 100));

    });
  }

  getQuantidadeArquivos(): number {
    return this.arquivos.length;
  }

  getArquivosProcessados(): number {

    return this.arquivos.filter(
      arquivo => arquivo.status === 'PROCESSADO'
    ).length;

  }

  getTamanhoFormatado(bytes: number): string {

    if (bytes < 1024) return bytes + ' B';

    if (bytes < 1024 * 1024) {
      return (bytes / 1024).toFixed(1) + ' KB';
    }

    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';

  }

}
