export interface IngestaoResponse {
  mensagem: string;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
}

export interface ArquivoFila {
  file: File;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
  progresso: number;
}
