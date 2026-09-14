import { CommonModule } from '@angular/common';
import { AfterViewChecked, AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, interval, Subscription, switchMap } from 'rxjs';
import { GraficoIaComponent } from '../grafico-ia/grafico-ia';
import { GrafoAtividadesComponent } from '../grafo-atividades/grafo-atividades.component';
import { LeaderboardUgComponent } from '../leaderboard-ug/leaderboard-ug.component';
import { AgentService, Notificacao, PainelAtrasos, RelatorioHistoricoItem, UsoIa } from '../services/agent.service';
import { AdminService } from '../services/admin.service';
import { SafeUrlPipe } from '../pipes/safe-url.pipe';

interface ChatSession {
  id: string;
  titulo: string;
  carregada?: boolean;
  fixado?: boolean;
  messages: { from: 'bot' | 'user'; text?: string; spec?: any; fontes?: string[]; sugestoes?: string[]; salvandoPainel?: boolean; painelSalvo?: boolean; interacaoId?: number; feedbackAberto?: boolean; feedbackTexto?: string; feedbackEnviado?: boolean }[];
}

@Component({
  selector: 'app-agent',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, GraficoIaComponent, SafeUrlPipe, LeaderboardUgComponent, GrafoAtividadesComponent],
  templateUrl: './agent.html',
  styleUrls: ['./agent.css']
})
export class AgentComponent implements OnInit, AfterViewInit, AfterViewChecked, OnDestroy {
  private static readonly SELECTED_EQUIPE_KEY = 'bicentral_selected_equipe';
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('promptInput') private promptInput?: ElementRef<HTMLTextAreaElement>;

  private static readonly AVISO_API_DISPENSADO_KEY = 'bicentral_aviso_api_openai_dispensado';
  private static readonly AVISO_TESTER_DISPENSADO_KEY = 'bicentral_aviso_tester_dispensado';

  isDarkMode = false;
  painelAtivo: 'chat' | 'ranking' | 'grafo' = 'chat';
  avisoApiVisivel = localStorage.getItem(AgentComponent.AVISO_API_DISPENSADO_KEY) !== '1';
  avisoTesterVisivel = localStorage.getItem(AgentComponent.AVISO_TESTER_DISPENSADO_KEY) !== '1';
  private scrollPendente = true;
  usuarioLogado = 'dallyla.moraes';
  equipeSelecionada = 'Orçamento';
  equipeId?: number;

  modelos = ['Gemini 2.5 Flash', 'Ollama Local'];
  modeloAtivoIndex = 0;

  sessoes: ChatSession[] = [
    { id: String(Date.now()), titulo: 'Nova Conversa', messages: [] }
  ];
  sessaoAtual: ChatSession = this.sessoes[0];

  input = '';
  carregando = false;
  erro = '';
  mensagemCopiadaId = '';
  private mensagemCopiadaTimer?: number;

  mensagemBoasVindas = '';
  isAdminSistema = false;

  private static readonly SIDEBAR_KEY = 'bicentral_sidebar_colapsada';
  private static readonly MOBILE_BREAKPOINT = 768;
  // Em telas pequenas a sidebar vira um overlay (ver agent.css) — começa fechada pra não
  // cobrir o chat inteiro assim que a tela abre; em telas maiores começa aberta como sempre.
  sidebarColapsada = window.innerWidth <= AgentComponent.MOBILE_BREAKPOINT;

  private static readonly FONT_SIZE_KEY = 'bicentral_font_size';
  private static readonly FONT_FAMILY_KEY = 'bicentral_font_family';
  mostrarSettings = false;
  fontSize: 'small' | 'medium' | 'large' = 'medium';
  fontFamily: 'default' | 'serif' | 'rounded' = 'default';

  notificacoes: Notificacao[] = [];
  carregandoNotificacoes = false;
  mostrarPainelNotificacoes = false;
  tarefasExpandidas = new Set<number>();

  painelAtrasos: PainelAtrasos | null = null;
  carregandoPainelAtrasos = false;
  erroPainelAtrasos = false;

  mostrarPainelRelatorio = false;
  meusRelatorios: RelatorioHistoricoItem[] = [];
  carregandoMeusRelatorios = false;
  relatorioExcluindoId?: number;
  relatorioPdfGerandoId?: number;
  private relatorioPollingTimer?: number;

  constructor(private agentService: AgentService, private adminService: AdminService, private router: Router) {
    this.carregarUsuario();
    this.carregarEquipeSelecionada();
    this.carregarNotificacoes();
    this.verificarAdminSistema();
    this.verificarTarefasAtrasadas();
    this.carregarUsoIa();

    if (localStorage.getItem('theme') === 'dark') {
      this.isDarkMode = true;
    }

    if (localStorage.getItem(AgentComponent.SIDEBAR_KEY) === '1') {
      this.sidebarColapsada = true;
    }

    const tamanhoSalvo = localStorage.getItem(AgentComponent.FONT_SIZE_KEY);
    if (tamanhoSalvo === 'small' || tamanhoSalvo === 'medium' || tamanhoSalvo === 'large') {
      this.fontSize = tamanhoSalvo;
    }

    const tipoSalvo = localStorage.getItem(AgentComponent.FONT_FAMILY_KEY);
    if (tipoSalvo === 'default' || tipoSalvo === 'serif' || tipoSalvo === 'rounded') {
      this.fontFamily = tipoSalvo;
    }
  }

  ngOnInit() {
    this.gerarMensagemBoasVindas();
    this.carregarSessoes();
  }

  private carregarSessoes(): void {
    this.agentService.listarSessoes().subscribe({
      next: (lista) => {
        if (lista.length === 0) return; // sem histórico ainda, mantém a "Nova Conversa" padrão
        this.sessoes = lista.map((s) => ({ id: s.id, titulo: s.titulo, fixado: s.fixado, messages: [], carregada: false }));
        this.selecionarChat(this.sessoes[0]);
      },
      error: () => { /* silencioso — começa do zero se falhar */ }
    });
  }

  // ==========================================
  // MENU "..." DE CADA CONVERSA (renomear / fixar / compartilhar / excluir)
  // ==========================================
  menuAbertoId: string | null = null;
  renomeandoId: string | null = null;
  tituloEditando = '';
  sessaoParaExcluir: ChatSession | null = null;
  compartilhandoId: string | null = null;
  linkCompartilhado: string | null = null;
  linkCopiado = false;

  get sessoesFixadas(): ChatSession[] {
    return this.sessoes.filter((s) => s.fixado);
  }

  get sessoesRecentes(): ChatSession[] {
    return this.sessoes.filter((s) => !s.fixado);
  }

  // Fecha o menu "..." se o clique foi fora dele — os botões que abrem/agem nesse menu chamam
  // stopPropagation(), então só chega aqui clique em qualquer outro lugar da tela.
  @HostListener('document:click')
  aoClicarFora(): void {
    this.menuAbertoId = null;
  }

  toggleMenuSessao(sessao: ChatSession, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbertoId = this.menuAbertoId === sessao.id ? null : sessao.id;
  }

  iniciarRenomear(sessao: ChatSession, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbertoId = null;
    this.renomeandoId = sessao.id;
    this.tituloEditando = sessao.titulo;

    // O input só existe no DOM depois que o Angular processar essa mudança — por isso o foco
    // é agendado pro próximo frame, em vez de tentar focar antes dele existir. Seleciona tudo
    // de propósito: digitar já substitui o título inteiro, sem precisar apagar na mão antes.
    window.requestAnimationFrame(() => {
      const input = document.querySelector<HTMLInputElement>('.chat-history-rename-input');
      input?.focus();
      input?.select();
    });
  }

  confirmarRenomear(sessao: ChatSession): void {
    const novoTitulo = this.tituloEditando.trim();
    this.renomeandoId = null;
    if (!novoTitulo || novoTitulo === sessao.titulo) return;

    sessao.titulo = novoTitulo;
    this.agentService.renomearSessao(sessao.id, novoTitulo).subscribe({
      error: () => { this.erro = 'Não foi possível renomear a conversa agora.'; }
    });
  }

  cancelarRenomear(): void {
    this.renomeandoId = null;
  }

  toggleFixar(sessao: ChatSession, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbertoId = null;
    const novoValor = !sessao.fixado;
    sessao.fixado = novoValor;
    this.agentService.fixarSessao(sessao.id, novoValor).subscribe({
      error: () => {
        sessao.fixado = !novoValor;
        this.erro = 'Não foi possível fixar a conversa agora.';
      }
    });
  }

  compartilharChat(sessao: ChatSession, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbertoId = null;

    if (sessao.messages.length === 0 && !sessao.carregada) {
      this.erro = 'Envie ao menos uma mensagem antes de compartilhar essa conversa.';
      return;
    }

    this.compartilhandoId = sessao.id;
    this.agentService.compartilharSessao(sessao.id).subscribe({
      next: ({ token }) => {
        this.compartilhandoId = null;
        this.linkCopiado = false;
        this.linkCompartilhado = `${window.location.origin}/chat-compartilhado/${token}`;
      },
      error: () => {
        this.compartilhandoId = null;
        this.erro = 'Não foi possível gerar o link agora.';
      }
    });
  }

  copiarLinkCompartilhado(): void {
    if (!this.linkCompartilhado) return;
    navigator.clipboard?.writeText(this.linkCompartilhado)
      .then(() => {
        this.linkCopiado = true;
        window.setTimeout(() => (this.linkCopiado = false), 1800);
      })
      .catch(() => { /* clipboard indisponível — o link já está visível pra copiar manualmente */ });
  }

  fecharCompartilhar(): void {
    this.linkCompartilhado = null;
  }

  selecionarTudo(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  pedirExclusao(sessao: ChatSession, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbertoId = null;
    this.sessaoParaExcluir = sessao;
  }

  cancelarExclusao(): void {
    this.sessaoParaExcluir = null;
  }

  confirmarExclusao(): void {
    const sessao = this.sessaoParaExcluir;
    if (!sessao) return;
    this.sessaoParaExcluir = null;

    // Uma "Nova Conversa" sem nenhuma mensagem ainda não existe no banco (só é criada lá na
    // primeira mensagem) — nesse caso só remove localmente, sem chamar o backend à toa.
    if (sessao.messages.length === 0 && !sessao.carregada) {
      this.removerSessaoLocal(sessao);
      return;
    }

    this.agentService.excluirSessao(sessao.id).subscribe({
      next: () => this.removerSessaoLocal(sessao),
      error: () => { this.erro = 'Não foi possível excluir a conversa agora.'; }
    });
  }

  private removerSessaoLocal(sessao: ChatSession): void {
    this.sessoes = this.sessoes.filter((s) => s.id !== sessao.id);
    if (this.sessaoAtual.id === sessao.id) {
      if (this.sessoes.length > 0) {
        this.selecionarChat(this.sessoes[0]);
      } else {
        this.iniciarNovoChat();
      }
    }
  }

  sair(): void {
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    this.router.navigate(['/login']);
  }

  verificarAdminSistema(): void {
    this.adminService.souAdmin().subscribe({
      next: (resposta) => {
        this.isAdminSistema = resposta.admin;
      },
      error: () => {
        this.isAdminSistema = false;
      }
    });
  }

  usoIa: UsoIa | null = null;

  carregarUsoIa(): void {
    this.agentService.consultarUsoIa().subscribe({
      next: (uso) => { this.usoIa = uso; },
      error: () => { /* silencioso — não é crítico pro chat funcionar */ }
    });
  }

  get usoIaPercentual(): number {
    if (!this.usoIa || this.usoIa.limite <= 0) return 0;
    return Math.min(100, (this.usoIa.gastoTotal / this.usoIa.limite) * 100);
  }

  get usoIaCritico(): boolean {
    return this.usoIaPercentual >= 85;
  }

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
      `Vamos explorar os dados da PROAP, ${nome}?`,
      `${saudacaoTempo}! Qual indicador vamos investigar agora, ${nome}?`,
      `${nome}, que dados vamos transformar em conhecimento hoje?`,
      `Como posso otimizar o seu planejamento hoje, ${nome}?`,
      `${saudacaoTempo}, ${nome}! Bora dar uma olhada nos números da PROAP?`,
      `${nome}, precisa de um gráfico rápido ou prefere só bater um papo com os dados?`,
      `${saudacaoTempo}! Sobre o que a gente conversa hoje, ${nome}?`,
      `${nome}, quer ver como anda o ranking das unidades hoje?`,
      `Tem algum indicador te tirando o sono, ${nome}? Bora resolver.`,
      `${saudacaoTempo}, ${nome}. Posso ajudar com o PAT, relatórios ou algum painel — é só pedir.`,
      `${nome}, bora transformar dado bruto em decisão?`,
      `Diz aí, ${nome}: o que você precisa saber sobre a PROAP agora?`
    ];

    const randomIndex = Math.floor(Math.random() * frases.length);
    this.mensagemBoasVindas = frases[randomIndex];
  }

  toggleTheme() {
    this.isDarkMode = !this.isDarkMode;
    localStorage.setItem('theme', this.isDarkMode ? 'dark' : 'light');
  }

  iniciarNovoChat() {
    const novaSessao: ChatSession = {
      id: String(Date.now()),
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

    if (sessao.carregada) return;

    this.agentService.listarMensagens(sessao.id).subscribe({
      next: (mensagens) => {
        sessao.messages = mensagens.map((m) => ({
          from: m.remetente,
          text: m.texto,
          spec: m.spec,
          fontes: m.fontes || undefined,
          sugestoes: m.sugestoes || undefined,
          interacaoId: m.interacaoId || undefined,
          feedbackEnviado: m.feedbackEnviado
        }));
        sessao.carregada = true;
        this.agendarScrollParaFim();
      },
      error: () => { /* mantém a sessão vazia se falhar */ }
    });
  }

  private static readonly PALAVRAS_GRAFICO = [
    'gráfico', 'grafico', 'painel', 'indicador', 'pizza', 'barra',
    'kpi', 'velocímetro', 'velocimetro', 'combo', 'gauge'
  ];

  private acordandoServidor = false;
  private consultaSub?: Subscription;
  private acordarServidorTimer?: number;

  // etapaAtual vem do polling no backend (qual ferramenta está rodando agora — ver
  // iniciarPollingStatus) e tem prioridade sobre o palpite por palavra-chave abaixo, que
  // só serve de fallback pro instante antes da primeira resposta do polling chegar.
  etapaAtual: string | null = null;
  private statusPollingSub?: Subscription;

  get textoPensando(): string {
    if (this.acordandoServidor) {
      return 'Servidor estava inativo, reconectando (pode levar até 1 minuto)';
    }
    if (this.etapaAtual) {
      return this.etapaAtual;
    }
    const ultimaDoUsuario = [...(this.sessaoAtual?.messages || [])].reverse().find(m => m.from === 'user');
    const texto = (ultimaDoUsuario?.text || '').toLowerCase();
    const pareceGrafico = AgentComponent.PALAVRAS_GRAFICO.some(p => texto.includes(p));
    return pareceGrafico ? 'Montando o painel' : 'Pensando';
  }

  // Faz polling no backend enquanto carregando=true pra saber qual ferramenta está rodando
  // agora (ver StatusExecucaoAgente) — não é streaming de tokens, é streaming "de etapa": dá
  // pra mostrar "Consultando o PAT da AUDIN..." em vez de um "Pensando" parado sem contexto.
  private iniciarPollingStatus(): void {
    this.pararPollingStatus();
    this.statusPollingSub = interval(800)
      .pipe(switchMap(() => this.agentService.consultarStatusExecucao()))
      .subscribe({
        next: (resposta) => this.etapaAtual = resposta.etapa,
        error: () => { } // falha no polling não trava o chat, só perde o texto específico
      });
  }

  private pararPollingStatus(): void {
    this.statusPollingSub?.unsubscribe();
    this.statusPollingSub = undefined;
    this.etapaAtual = null;
  }

  // Render (free tier) derruba o backend depois de um tempo sem uso — a primeira
  // mensagem depois disso costuma falhar (500/502/503/timeout) e só funciona ao
  // reenviar, porque aí o servidor já acordou. Em vez de mostrar erro assustador pro
  // tester de cara, tenta de novo uma vez, silenciosamente, antes de desistir.
  private static readonly STATUS_PROVAVEL_SERVIDOR_DORMINDO = [0, 500, 502, 503, 504];

  send() {
    const text = (this.input || '').trim();
    if (!text || this.carregando) return;

    if (this.sessaoAtual.titulo === 'Nova Conversa') {
      this.sessaoAtual.titulo = text.substring(0, 25) + (text.length > 25 ? '...' : '');
    }

    this.sessaoAtual.messages.push({ from: 'user', text });
    this.input = '';
    this.agendarAjusteAlturaPrompt();
    this.erro = '';
    this.carregando = true;
    this.iniciarPollingStatus();
    this.agendarScrollParaFim();

    const idDaSessao = String(this.sessaoAtual.id);
    this.enviarConsulta(text, idDaSessao, false);
  }

  // Some o botão de enviar e mostra um de parar enquanto carregando — clicar nele cancela
  // a chamada HTTP em andamento (unsubscribe aborta o request), avisa o backend pra tentar
  // interromper de verdade a geração em andamento (best-effort, ver AgentService.
  // cancelarGeracao) e, se estava no meio do retry silencioso de servidor dormindo, cancela
  // o setTimeout também.
  pararGeracao(): void {
    if (this.acordarServidorTimer) {
      window.clearTimeout(this.acordarServidorTimer);
      this.acordarServidorTimer = undefined;
    }
    this.consultaSub?.unsubscribe();
    this.consultaSub = undefined;
    this.agentService.cancelarGeracao().subscribe({ error: () => {} });
    this.acordandoServidor = false;
    this.carregando = false;
    this.pararPollingStatus();
  }

  private enviarConsulta(text: string, idDaSessao: string, isRetry: boolean) {
    this.consultaSub = this.agentService.consultar(text, this.equipeId ?? null, this.modeloAtivo, idDaSessao)
      .pipe(finalize(() => {
        if (!this.acordandoServidor) {
          this.carregando = false;
          this.pararPollingStatus();
          this.carregarUsoIa();
        }
      }))
      .subscribe({
        next: (resposta: any) => {
          this.acordandoServidor = false;

          if (resposta.skill === 'painel') {
            this.sessaoAtual.messages.push({
              from: 'bot',
              text: resposta.mensagemContexto || 'Aqui está a visualização dos dados:',
              spec: resposta,
              fontes: resposta.fontes,
              interacaoId: resposta.interacaoId
            });
          } else if (resposta.texto) {
            this.sessaoAtual.messages.push({
              from: 'bot',
              text: resposta.texto,
              fontes: resposta.fontes,
              sugestoes: resposta.sugestoes,
              interacaoId: resposta.interacaoId
            });

            if (resposta.relatorioGerado) {
              this.abrirPainelRelatorios();
            }
          } else {
            this.sessaoAtual.messages.push({ from: 'bot', text: resposta });
          }

          this.agendarScrollParaFim();
        },
        error: (err) => {
          const provavelServidorDormindo = AgentComponent.STATUS_PROVAVEL_SERVIDOR_DORMINDO.includes(err?.status);

          if (!isRetry && provavelServidorDormindo) {
            this.acordandoServidor = true;
            this.acordarServidorTimer = window.setTimeout(() => this.enviarConsulta(text, idDaSessao, true), 3000);
            return;
          }

          this.acordandoServidor = false;
          this.carregando = false;
          const mensagem = err?.error?.mensagem || 'Não foi possível consultar o agente agora.';
          this.erro = mensagem;
          this.sessaoAtual.messages.push({ from: 'bot', text: mensagem });
          this.agendarScrollParaFim();
        }
      });
  }

  enviarSugestao(texto: string) {
    this.input = texto;
    this.send();
  }

  salvarPainel(mensagem: { spec?: any; salvandoPainel?: boolean; painelSalvo?: boolean }): void {
    if (!mensagem.spec || mensagem.salvandoPainel || mensagem.painelSalvo) return;

    mensagem.salvandoPainel = true;
    this.agentService.salvarPainelIa(mensagem.spec.titulo, mensagem.spec).subscribe({
      next: () => {
        mensagem.salvandoPainel = false;
        mensagem.painelSalvo = true;
      },
      error: () => {
        mensagem.salvandoPainel = false;
        this.erro = 'Não foi possível salvar o painel agora.';
      }
    });
  }

  alternarFeedback(mensagem: { feedbackAberto?: boolean }): void {
    mensagem.feedbackAberto = !mensagem.feedbackAberto;
  }

  enviarFeedback(mensagem: { interacaoId?: number; feedbackTexto?: string; feedbackAberto?: boolean; feedbackEnviado?: boolean }): void {
    if (!mensagem.interacaoId || !mensagem.feedbackTexto) return;

    this.agentService.enviarFeedbackInteracao(mensagem.interacaoId, mensagem.feedbackTexto).subscribe({
      next: () => {
        mensagem.feedbackAberto = false;
        mensagem.feedbackEnviado = true;
      },
      error: () => {
        this.erro = 'Não foi possível enviar o feedback agora.';
      }
    });
  }

  dispensarAvisoApi(): void {
    this.avisoApiVisivel = false;
    localStorage.setItem(AgentComponent.AVISO_API_DISPENSADO_KEY, '1');
  }

  dispensarAvisoTester(): void {
    this.avisoTesterVisivel = false;
    localStorage.setItem(AgentComponent.AVISO_TESTER_DISPENSADO_KEY, '1');
  }

  get sugestoesAtuais(): string[] {
    const msgs = this.sessaoAtual?.messages;
    if (!msgs || msgs.length === 0) return [];
    const ultima = msgs[msgs.length - 1];
    return ultima.from === 'bot' && ultima.sugestoes ? ultima.sugestoes : [];
  }

  ngAfterViewInit(): void { this.agendarScrollParaFim(); }

  ngOnDestroy(): void {
    this.pararPollingRelatorio();
    if (this.acordarServidorTimer) window.clearTimeout(this.acordarServidorTimer);
    this.consultaSub?.unsubscribe();
    this.statusPollingSub?.unsubscribe();
  }

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

  // Mesma chave escopada por usuário que HomeComponent/EquipeComponent usam pra salvar
  // (ver bug corrigido em Neci/feature/auditoria-convites-membros: chave sem escopo
  // fazia um usuário herdar a equipe selecionada de outro). Sem isso aqui, essa tela
  // nunca acha a equipe salva e sempre pede "selecione uma equipe".
  private getEquipeStorageKey(): string | null {
    try {
      const userRaw = localStorage.getItem('user');
      if (!userRaw) return null;
      const user = JSON.parse(userRaw) as { id?: string | number };
      if (!user?.id) return null;
      return `${AgentComponent.SELECTED_EQUIPE_KEY}:${user.id}`;
    } catch {
      return null;
    }
  }

  private carregarEquipeSelecionada(): void {
    try {
      const key = this.getEquipeStorageKey();
      if (!key) return;
      const raw = localStorage.getItem(key);
      if (raw) {
        const equipe = JSON.parse(raw);
        this.equipeId = equipe.id;
        this.equipeSelecionada = equipe.nome;
      }
    } catch { }
  }

  toggleSidebar(): void {
    this.sidebarColapsada = !this.sidebarColapsada;
    localStorage.setItem(AgentComponent.SIDEBAR_KEY, this.sidebarColapsada ? '1' : '0');
  }

  toggleSettings(): void {
    this.mostrarSettings = !this.mostrarSettings;
    if (this.mostrarSettings) {
      this.mostrarPainelNotificacoes = false;
      this.mostrarPainelRelatorio = false;
    }
  }

  setFontSize(tamanho: 'small' | 'medium' | 'large'): void {
    this.fontSize = tamanho;
    localStorage.setItem(AgentComponent.FONT_SIZE_KEY, tamanho);
  }

  setFontFamily(tipo: 'default' | 'serif' | 'rounded'): void {
    this.fontFamily = tipo;
    localStorage.setItem(AgentComponent.FONT_FAMILY_KEY, tipo);
  }

  private verificarTarefasAtrasadas(): void {
    this.agentService.buscarMinhasTarefasAtrasadas().subscribe({
      next: (resumo) => {
        if (resumo.quantidade > 0 && this.sessaoAtual.messages.length === 0) {
          const dias = resumo.diasAtraso ?? 0;
          const diaOuDias = dias === 1 ? 'dia' : 'dias';
          const texto = resumo.quantidade === 1
            ? `⚠️ Antes de começarmos: você tem 1 tarefa atrasada — "${resumo.tituloMaisUrgente}", há ${dias} ${diaOuDias}. Quer que eu liste os detalhes?`
            : `⚠️ Antes de começarmos: você tem ${resumo.quantidade} tarefas atrasadas. A mais urgente é "${resumo.tituloMaisUrgente}", há ${dias} ${diaOuDias}. Quer que eu liste todas?`;

          this.sessaoAtual.messages.push({
            from: 'bot',
            text: texto,
            sugestoes: ['Quais são minhas tarefas atrasadas?']
          });
        }
      },
      error: () => { /* silencioso: uma falha aqui não pode travar a abertura do chat */ }
    });
  }

  private carregarNotificacoes(): void {
    this.carregandoNotificacoes = true;
    this.agentService.listarNotificacoes()
      .pipe(finalize(() => this.carregandoNotificacoes = false))
      .subscribe({
        next: (lista) => this.notificacoes = lista || [],
        error: () => this.notificacoes = []
      });
  }

  toggleNotificacoes(): void {
    this.mostrarPainelNotificacoes = !this.mostrarPainelNotificacoes;
    if (this.mostrarPainelNotificacoes) {
      this.mostrarPainelRelatorio = false;
      this.mostrarSettings = false;
    }
  }

  get temAlertaNegativo(): boolean {
    return this.notificacoes.some(n => ['⚠️', '📉', '⏸️'].includes(n.emoji));
  }

  toggleTarefas(i: number): void {
    if (this.tarefasExpandidas.has(i)) {
      this.tarefasExpandidas.delete(i);
    } else {
      this.tarefasExpandidas.add(i);
    }
  }

  abrirPainelAtrasos(departamento: string): void {
    this.mostrarPainelNotificacoes = false;
    this.carregandoPainelAtrasos = true;
    this.erroPainelAtrasos = false;
    this.agentService.buscarPainelAtrasos(departamento)
      .pipe(finalize(() => this.carregandoPainelAtrasos = false))
      .subscribe({
        next: (painel) => this.painelAtrasos = painel,
        error: () => {
          this.painelAtrasos = null;
          this.erroPainelAtrasos = true;
        }
      });
  }

  fecharPainelAtrasos(): void {
    this.painelAtrasos = null;
  }

  toggleRelatorio(): void {
    this.mostrarPainelRelatorio = !this.mostrarPainelRelatorio;
    if (this.mostrarPainelRelatorio) {
      this.mostrarPainelNotificacoes = false;
      this.mostrarSettings = false;
      this.carregarMeusRelatorios();
      this.iniciarPollingHistorico();
    } else {
      this.pararPollingRelatorio();
    }
  }

  /** Abre o painel de relatórios sozinho (ex: quando o agente gera um relatório pelo chat). */
  private abrirPainelRelatorios(): void {
    this.mostrarPainelRelatorio = true;
    this.mostrarPainelNotificacoes = false;
    this.carregarMeusRelatorios();
    this.iniciarPollingHistorico();
  }

  paraDataUtc(valor: string | null): Date | null {
    if (!valor) return null;
    const temFuso = /(Z|[+-]\d{2}:?\d{2})$/.test(valor);
    const data = new Date(temFuso ? valor : valor + 'Z');
    return isNaN(data.getTime()) ? null : data;
  }

  private carregarMeusRelatorios(): void {
    this.carregandoMeusRelatorios = true;
    this.agentService.listarMeusRelatorios().subscribe({
      next: (lista) => {
        this.meusRelatorios = lista;
        this.carregandoMeusRelatorios = false;
      },
      error: () => {
        this.carregandoMeusRelatorios = false;
      }
    });
  }

  private iniciarPollingHistorico(): void {
    this.pararPollingRelatorio();
    this.relatorioPollingTimer = window.setInterval(() => {
      const temProcessando = this.meusRelatorios.some(r => r.status === 'PROCESSANDO');
      if (!temProcessando) {
        this.pararPollingRelatorio();
        return;
      }
      this.carregarMeusRelatorios();
    }, 5000);
  }

  private pararPollingRelatorio(): void {
    if (this.relatorioPollingTimer) {
      window.clearInterval(this.relatorioPollingTimer);
      this.relatorioPollingTimer = undefined;
    }
  }

  abrirPdfRelatorio(relatorio: RelatorioHistoricoItem, event: MouseEvent): void {
    event.stopPropagation();
    if (relatorio.status !== 'PRONTO' || this.relatorioPdfGerandoId) return;

    if (relatorio.pdf_url) {
      window.open(relatorio.pdf_url, '_blank', 'noopener');
      return;
    }

    this.relatorioPdfGerandoId = relatorio.id;
    this.agentService.gerarPdfRelatorio(relatorio.id)
      .pipe(finalize(() => this.relatorioPdfGerandoId = undefined))
      .subscribe({
        next: (resposta) => {
          relatorio.pdf_url = resposta.pdf_url;
          window.open(resposta.pdf_url, '_blank', 'noopener');
        },
        error: () => {
          this.erro = 'Não foi possível gerar o PDF deste relatório agora.';
        }
      });
  }

  excluirRelatorio(relatorio: RelatorioHistoricoItem, event: MouseEvent): void {
    event.stopPropagation();

    const nome = relatorio.departamento || 'este relatório';
    const confirmar = window.confirm(`Excluir o relatório "${nome}"?`);
    if (!confirmar || this.relatorioExcluindoId) return;

    this.relatorioExcluindoId = relatorio.id;
    this.agentService.excluirRelatorio(relatorio.id)
      .pipe(finalize(() => this.relatorioExcluindoId = undefined))
      .subscribe({
        next: () => {
          this.meusRelatorios = this.meusRelatorios.filter((item) => item.id !== relatorio.id);
        },
        error: () => {
          this.erro = 'Não foi possível excluir o relatório agora.';
        }
      });
  }

  private agendarScrollParaFim(): void { this.scrollPendente = true; }

  formatarMensagem(texto: string | undefined): string {
    if (!texto) return '';

    const linhas = texto.split(/\r?\n/);
    const html: string[] = [];
    let listaAberta = false;

    for (let i = 0; i < linhas.length; i++) {
      const linha = linhas[i];
      const proximaLinha = linhas[i + 1];

      if (this.ehLinhaTabela(linha) && this.ehSeparadorTabela(proximaLinha)) {
        if (listaAberta) {
          html.push('</ul>');
          listaAberta = false;
        }

        const linhasTabela = [linha];
        i += 2;

        while (i < linhas.length && this.ehLinhaTabela(linhas[i])) {
          linhasTabela.push(linhas[i]);
          i++;
        }

        i--;
        html.push(this.formatarTabela(linhasTabela));
        continue;
      }

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

      if (linha.trim() === '') continue;

      const titulo = linha.match(/^(#{1,3})\s+(.+)$/);
      if (titulo) {
        const nivel = titulo[1].length + 2;
        html.push(`<h${nivel}>${this.formatarInline(titulo[2])}</h${nivel}>`);
        continue;
      }

      html.push(`<p>${this.formatarInline(linha)}</p>`);
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

  private formatarTabela(linhas: string[]): string {
    const [cabecalho, ...corpo] = linhas.map((linha) => this.quebrarLinhaTabela(linha));

    const ths = cabecalho
      .map((celula) => `<th>${this.formatarInline(celula)}</th>`)
      .join('');
    const trs = corpo
      .map((linha) => `<tr>${linha.map((celula) => `<td>${this.formatarInline(celula)}</td>`).join('')}</tr>`)
      .join('');

    return `<div class="message-table-wrap"><table><thead><tr>${ths}</tr></thead><tbody>${trs}</tbody></table></div>`;
  }

  private quebrarLinhaTabela(linha: string): string[] {
    return linha
      .trim()
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((celula) => celula.trim());
  }

  private ehLinhaTabela(linha: string | undefined): boolean {
    if (!linha) return false;
    const texto = linha.trim();
    return texto.startsWith('|') && texto.endsWith('|') && texto.includes('|');
  }

  private ehSeparadorTabela(linha: string | undefined): boolean {
    if (!this.ehLinhaTabela(linha)) return false;
    return this.quebrarLinhaTabela(linha || '').every((celula) => /^:?-{3,}:?$/.test(celula));
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