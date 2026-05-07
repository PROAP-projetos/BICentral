import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface ArquivoUpload {
  arquivo: File;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
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

  equipeSelecionada = '';

  visibilidade = 'PUBLICO';

  carregando = false;

  mensagem = '';

  erro = false;

  equipes = [
    'COMUNICACAO',
    'FINANCEIRO',
    'PROAP',
    'ADMINISTRACAO'
  ];

  onArquivosSelecionados(event: Event): void {
    const input = event.target as HTMLInputElement;

    if (!input.files) return;

    for (let i = 0; i < input.files.length; i++) {
      const arquivo = input.files[i];

      this.arquivos.push({
        arquivo,
        status: 'AGUARDANDO'
      });
    }
  }

  removerArquivo(index: number): void {
    this.arquivos.splice(index, 1);
  }

  confirmarIngestao(): void {

    if (this.arquivos.length === 0) {
      this.erro = true;
      this.mensagem = 'Selecione ao menos um arquivo.';
      return;
    }

    if (!this.equipeSelecionada) {
      this.erro = true;
      this.mensagem = 'Selecione uma equipe.';
      return;
    }

    this.carregando = true;
    this.erro = false;
    this.mensagem = '';

    this.arquivos.forEach(arquivo => {
      arquivo.status = 'PROCESSANDO';
    });

    setTimeout(() => {

      this.arquivos.forEach(arquivo => {
        arquivo.status = 'PROCESSADO';
      });

      this.carregando = false;
      this.mensagem = 'Documentos enviados para ingestão com sucesso.';

    }, 2000);
  }

  getQuantidadeArquivos(): number {
    return this.arquivos.length;
  }

}
