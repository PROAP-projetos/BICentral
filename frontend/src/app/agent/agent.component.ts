import { CommonModule } from '@angular/common';
import { AfterViewChecked, AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { GraficoIaComponent } from '../grafico-ia/grafico-ia';
import { AgentService } from '../services/agent.service';
import { SafeUrlPipe } from '../pipes/safe-url.pipe';
interface FonteDisponivel {
  nome: string;
  acesso: 'publico' | 'privado';
  tipo: 'planilha' | 'pdf' | 'relatorio' | 'documento';
}

interface ChatSession {
  id: number;
  titulo: string;
  messages: { from: 'bot' | 'user'; text?: string; spec?: any; fontes?: string[] }[];
}

@Component({
  selector: 'app-agent',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, GraficoIaComponent, SafeUrlPipe],
  templateUrl: './agent.html',
  styleUrls: ['./agent.css']
})
export class AgentComponent implements OnInit, AfterViewInit, AfterViewChecked {
  private static readonly SELECTED_EQUIPE_KEY = 'bicentral_selected_equipe';
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('promptInput') private promptInput?: ElementRef<HTMLTextAreaElement>;

  isDarkMode = false;
  private scrollPendente = true;
  usuarioLogado = 'dallyla.moraes';
  equipeSelecionada = 'Orçamento';
  equipeId?: number;
  fontesDisponiveis: FonteDisponivel[] = [];

  modelos = ['Llama 3 (Groq)', 'Gemini 2.5 Flash', 'Ollama Local'];
  modeloAtivoIndex = 0;

  sessoes: ChatSession[] = [
    { id: 1, titulo: 'Nova Conversa', messages: [] }
  ];
  sessaoAtual: ChatSession = this.sessoes[0];

  input = '';
  carregando = false;
  carregandoFontes = false;
  erro = '';
  mensagemCopiadaId = '';
  private mensagemCopiadaTimer?: number;

  mensagemBoasVindas = '';

  constructor(private agentService: AgentService) {
    this.carregarUsuario();
    this.carregarEquipeSelecionada();
    this.carregarFontes();

    if (localStorage.getItem('theme') === 'dark') {
      this.isDarkMode = true;
    }
  }

  ngOnInit() {
    this.gerarMensagemBoasVindas();
  }

  // ==========================================
  // GETTERS DE INTERFACE
  // ==========================================
  get messages() {
    return this.sessaoAtual.messages;
  }

  get modeloAtivo() {
    return this.modelos[this.modeloAtivoIndex];
  }

  get nomeExibicao(): string {
    if (!this.usuarioLogado) return 'Usuário';
    const primeiroNome = this.usuarioLogado.split('.')[0];
    return primeiroNome.charAt(0).toUpperCase() + primeiroNome.slice(1);
  }

  // ==========================================
  // LÓGICA DE BOAS-VINDAS DINÂMICA
  // ==========================================
  private gerarMensagemBoasVindas() {
    const hora = new Date().getHours();
    let saudacaoTempo = 'Olá';

    if (hora >= 5 && hora < 12) saudacaoTempo = 'Bom dia';
    else if (hora >= 12 && hora < 18) saudacaoTempo = 'Boa tarde';
    else saudacaoTempo = 'Boa noite';

    const nome = this.nomeExibicao;

    const frases = [
      `${saudacaoTempo}, ${nome}. O que vamos analisar hoje?`,
      `${saudacaoTempo}, ${nome}! Quais as ideias criativas para hoje?`,
      `Pronta para explorar os dados da PROAP, ${nome}?`,
      `${saudacaoTempo}! Qual indicador vamos investigar agora, ${nome}?`,
      `${nome}, que dados vamos transformar em conhecimento hoje?`,
      `Como posso otimizar o seu planejamento hoje, ${nome}?`
    ];

    const randomIndex = Math.floor(Math.random() * frases.length);
    this.mensagemBoasVindas = frases[randomIndex];
  }

  // ==========================================
  // AÇÕES DO USUÁRIO
  // ==========================================
  toggleTheme() {
    this.isDarkMode = !this.isDarkMode;
    localStorage.setItem('theme', this.isDarkMode ? 'dark' : 'light');
  }

  mudarModelo() {
    this.modeloAtivoIndex = (this.modeloAtivoIndex + 1) % this.modelos.length;
  }

  iniciarNovoChat() {
    const novaSessao: ChatSession = {
      id: Date.now(),
      titulo: 'Nova Conversa',
      messages: []
    };
    this.sessoes.unshift(novaSessao);
    this.sessaoAtual = novaSessao;
    this.erro = '';
    this.gerarMensagemBoasVindas();
  }

  encodeURIComponent(url: string | null): string {
    return url ? encodeURIComponent(url) : '';
  }

  selecionarChat(sessao: ChatSession) {
    this.sessaoAtual = sessao;
    this.erro = '';
    this.agendarScrollParaFim();
  }

  send() {
    const text = (this.input || '').trim();
    if (!text || this.carregando) return;

    if (!this.equipeId) {
      this.erro = 'Selecione uma equipe antes de consultar o agente.';
      return;
    }

    if (this.sessaoAtual.titulo === 'Nova Conversa') {
      this.sessaoAtual.titulo = text.substring(0, 25) + (text.length > 25 ? '...' : '');
    }

    this.sessaoAtual.messages.push({ from: 'user', text });
    this.input = '';
    this.agendarAjusteAlturaPrompt();
    this.erro = '';
    this.carregando = true;
    this.agendarScrollParaFim();

    this.agentService.consultar(text, this.equipeId, this.modeloAtivo)
      .pipe(finalize(() => this.carregando = false))
      .subscribe({
        next: (resposta: any) => {

          // 1. Se a resposta for a configuração de um GRÁFICO
          if (resposta.skill === 'grafico') {
            this.sessaoAtual.messages.push({
              from: 'bot',
              text: resposta.mensagemContexto || 'Aqui está a visualização dos dados:',
              spec: resposta,
              fontes: resposta.fontes // Guarda as fontes lidas
            });
          }
          // 2. Se a resposta for um TEXTO NATURAL (RespostaTextual)
          else if (resposta.texto) {
            this.sessaoAtual.messages.push({
              from: 'bot',
              text: resposta.texto,
              fontes: resposta.fontes // Guarda as fontes lidas
            });
          }
          // 3. Fallback genérico caso o formato venha diferente
          else {
            this.sessaoAtual.messages.push({ from: 'bot', text: resposta });
          }

          this.agendarScrollParaFim();
        },
        error: (err) => {
          const mensagem = err?.error?.mensagem || 'Não foi possível consultar o agente agora.';
          this.erro = mensagem;
          this.sessaoAtual.messages.push({ from: 'bot', text: mensagem });
          this.agendarScrollParaFim();
        }
      });
  }

  // ==========================================
  // CICLO DE VIDA E CARREGAMENTOS
  // ==========================================
  ngAfterViewInit(): void { this.agendarScrollParaFim(); }

  ngAfterViewChecked(): void {
    if (!this.scrollPendente) return;
    this.scrollPendente = false;
    this.scrollMessagesToBottom();
  }

  private carregarUsuario() {
    try {
      const userRaw = localStorage.getItem('user');
      if (userRaw) this.usuarioLogado = JSON.parse(userRaw).username || 'dallyla.moraes';
    } catch { }
  }

  private carregarEquipeSelecionada(): void {
    try {
      const raw = localStorage.getItem(AgentComponent.SELECTED_EQUIPE_KEY);
      if (raw) {
        const equipe = JSON.parse(raw);
        this.equipeId = equipe.id;
        this.equipeSelecionada = equipe.nome;
      }
    } catch { }
  }

  private carregarFontes(): void {
    if (!this.equipeId) return;
    this.carregandoFontes = true;
    this.agentService.listarFontes(this.equipeId)
      .pipe(finalize(() => this.carregandoFontes = false))
      .subscribe({
        next: (response) => {
          this.fontesDisponiveis = (response.fontes || []).map((fonte) => ({
            ...fonte,
            tipo: this.getTipoFonte(fonte.nome)
          }));
        },
        error: () => this.fontesDisponiveis = []
      });
  }

  private getTipoFonte(nome: string): FonteDisponivel['tipo'] {
    const ext = nome.split('.').pop()?.toLowerCase();
    if (ext === 'xlsx') return 'planilha';
    if (ext === 'pdf') return 'pdf';
    if (nome.toLowerCase().includes('relatorio')) return 'relatorio';
    return 'documento';
  }

  private agendarScrollParaFim(): void { this.scrollPendente = true; }

  formatarMensagem(texto: string | undefined): string {
    if (!texto) return '';

    const linhas = texto.split(/\r?\n/);
    const html: string[] = [];
    let listaAberta = false;

    for (const linha of linhas) {
      const itemLista = linha.match(/^\s*[-*]\s+(.+)$/);

      if (itemLista) {
        if (!listaAberta) {
          html.push('<ul>');
          listaAberta = true;
        }
        html.push(`<li>${this.formatarInline(itemLista[1])}</li>`);
        continue;
      }

      if (listaAberta) {
        html.push('</ul>');
        listaAberta = false;
      }

      if (linha.trim() === '') {
        html.push('<br>');
      } else {
        html.push(`<p>${this.formatarInline(linha)}</p>`);
      }
    }

    if (listaAberta) html.push('</ul>');
    return html.join('');
  }

  getMensagemId(index: number, texto: string | undefined): string {
    return `${index}:${texto || ''}`;
  }

  copiarMensagem(texto: string | undefined, index: number): void {
    if (!texto) return;

    if (navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(texto)
        .then(() => this.marcarMensagemCopiada(index, texto))
        .catch(() => {
          this.copiarComFallback(texto);
          this.marcarMensagemCopiada(index, texto);
        });
      return;
    }

    this.copiarComFallback(texto);
    this.marcarMensagemCopiada(index, texto);
  }

  editarPrompt(texto: string | undefined): void {
    if (!texto) return;
    this.input = texto;
    this.agendarAjusteAlturaPrompt(true);
  }

  ajustarAlturaPrompt(event?: Event): void {
    const textarea = (event?.target as HTMLTextAreaElement | null) ?? this.promptInput?.nativeElement;
    if (!textarea) return;

    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`;
  }

  aoPressionarPrompt(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey) return;
    event.preventDefault();
    this.send();
  }

  private formatarInline(texto: string): string {
    return this.escaparHtml(texto)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/__(.+?)__/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>');
  }

  private escaparHtml(texto: string): string {
    return texto
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  private copiarComFallback(texto: string): void {
    const textarea = document.createElement('textarea');
    textarea.value = texto;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }

  private marcarMensagemCopiada(index: number, texto: string): void {
    this.mensagemCopiadaId = this.getMensagemId(index, texto);

    if (this.mensagemCopiadaTimer) {
      window.clearTimeout(this.mensagemCopiadaTimer);
    }

    this.mensagemCopiadaTimer = window.setTimeout(() => {
      this.mensagemCopiadaId = '';
    }, 1800);
  }

  private agendarAjusteAlturaPrompt(focar = false): void {
    window.requestAnimationFrame(() => {
      this.ajustarAlturaPrompt();
      if (focar) this.promptInput?.nativeElement.focus();
    });
  }

  private scrollMessagesToBottom(): void {
    window.requestAnimationFrame(() => {
      const container = this.messagesContainer?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    });
  }
  arquivoAberto: string | null = null;

  abrirDocumento(nome: string) {
    this.arquivoAberto = nome;
  }

  fecharViewer() {
    this.arquivoAberto = null;
  }
}
