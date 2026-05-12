export interface IngestaoResponse {
  mensagem: string;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
  totalChunks?: number;
  arquivo?: string;
  equipe?: string;
  visibilidade?: 'PUBLICO' | 'PRIVADO';
}

export interface ArquivoFila {
  file: File;
  status: 'AGUARDANDO' | 'PROCESSANDO' | 'PROCESSADO' | 'ERRO';
  progresso: number;
}
