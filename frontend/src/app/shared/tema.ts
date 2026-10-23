export interface CoresDoTema {
  texto: string;
  textoSuave: string;
  borda: string;
}

export function lerCoresDoTema(elemento: HTMLElement): CoresDoTema {
  const estilo = getComputedStyle(elemento);
  const valor = (nome: string, padrao: string) => estilo.getPropertyValue(nome).trim() || padrao;
  return {
    texto: valor('--text-primary', '#1f2937'),
    textoSuave: valor('--text-secondary', '#6b7280'),
    borda: valor('--border-color', '#e5e7eb')
  };
}

// Gráficos do ECharts não herdam CSS: ao trocar claro/escuro eles precisam ser redesenhados.
export function aoMudarTema(elemento: HTMLElement, aoMudar: () => void): () => void {
  let ultimo = lerCoresDoTema(elemento).texto;
  const observador = new MutationObserver(() => {
    const atual = lerCoresDoTema(elemento).texto;
    if (atual !== ultimo) {
      ultimo = atual;
      aoMudar();
    }
  });
  // Só os ancestrais, sem subtree: observar a página toda reativa a detecção de mudanças do Angular a cada [class.x] e entra em laço.
  for (let no: HTMLElement | null = elemento; no; no = no.parentElement) {
    observador.observe(no, { attributes: true, attributeFilter: ['class', 'data-theme'] });
  }
  return () => observador.disconnect();
}
