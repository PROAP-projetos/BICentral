import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { interval, Subscription, of } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { FormBuilder, ReactiveFormsModule, Validators, FormGroup } from '@angular/forms';
import { AdminService } from '../services/admin.service';
import { EquipeService, Equipe } from '../services/equipe.service';
import { AgentService, PainelIa } from '../services/agent.service';
import { GraficoIaComponent } from '../grafico-ia/grafico-ia';

// ✅ ajuste o path real do seu projeto
import { AddPainelComponent } from '../add-painel/add-painel.component';

interface PainelDTO {
id: number;
nome: string;
linkPowerBi: string;
imagemCapaUrl: string | null;
statusCaptura: string;
previewSrc?: string;
carregada?: boolean;
}

interface UsuarioLocalStorage {
  id?: string;
  token?: string;
  username?: string;
  email?: string;
  role?: string;
}

type UserRole = 'VIEWER' | 'EDITOR' | 'ADMIN';

interface EquipeSelecionada {
  id: number;
  nome: string;
  role: UserRole;
}

interface EquipeMenuItem {
  id: number;
  nome: string;
  role: UserRole;
}

@Component({
selector: 'app-home',
standalone: true,
imports: [CommonModule, RouterModule, ReactiveFormsModule, AddPainelComponent, GraficoIaComponent],
templateUrl: './home.html',
styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit, OnDestroy {
private static readonly SELECTED_EQUIPE_KEY = 'bicentral_selected_equipe';

dashboards: PainelDTO[] = [];
equipesMenu: EquipeMenuItem[] = [];
loading = true;
error: string | null = null;

// -------------------------
// Painéis de IA (gerados no chat do proIAp)
// -------------------------
paineisIa: PainelIa[] = [];
loadingPaineisIa = true;
excluindoPainelIaId: number | null = null;
confirmandoExclusaoPainelIaId: number | null = null;

isLoggedIn = false;
isAdminSistema = false;
userName: string | null = null;
currentRole: UserRole = 'VIEWER';
equipeSelecionada: EquipeSelecionada | null = null;
showWelcomeOverlay = false;
welcomeStep = 0;
isWelcomeClosing = false;
welcomeOutroTitle = 'Que bom ter voce aqui.';
welcomeOutroSubtitle = 'Preparando seu espaco para organizar paineis...';
private welcomeTimers: number[] = [];
agentBubbleVisible = false;
private agentBubbleTimer?: number;
showFooterSignature = false;
private footerSignatureTimer?: number;

readonly welcomeSlides = [
  {
    badge: 'Bem-vindo ao BICentral',
    title: 'O hub de BI da PROAP',
    text: 'Aqui ficam centralizados os painéis de Power BI da PROAP — um só lugar pra toda a equipe acompanhar os indicadores, sem caçar link espalhado.'
  },
  {
    badge: 'Conheça o proIAp',
    title: 'Peça um gráfico, ele aparece aqui do lado',
    text: 'O proIAp é o agente de IA do BICentral: converse com ele no chat, peça um indicador sobre os dados da PROAP, e o painel gerado fica salvo bem aqui, junto dos painéis de Power BI.'
  },
  {
    badge: 'Comece agora',
    title: 'Tudo o que você precisa, num só lugar',
    text: 'Adicione os painéis de Power BI da sua equipe e explore o proIAp pelo botão "Pergunte ao agente" — o BICentral organiza os dois pra você.'
  }
];

private pollingSub?: Subscription;

get canEdit(): boolean {
  return this.currentRole === 'ADMIN' || this.currentRole === 'EDITOR';
}

get apiUrl(): string | null {
  if (!this.equipeSelecionada?.id) {
    return null;
  }
  return `/api/equipes/${this.equipeSelecionada.id}/paineis`;
}

// -------------------------
// MODAL EDIÇÃO
// -------------------------
isEditOpen = false;
savingEdit = false;
editError: string | null = null;
editingPainel: PainelDTO | null = null;
editForm!: FormGroup;

// -------------------------
// MODAL EXCLUIR
// -------------------------
isDeleteOpen = false;
deleting = false;
deleteError: string | null = null;
deletingPainel: PainelDTO | null = null;
deleteMessage: string | null = null;
deleteSuccess = false;

// -------------------------
// ✅ ADICIONAR (AGORA É COMPONENTE POPUP)
// -------------------------
isAddOpen = false;

constructor(
    private http: HttpClient,
    private router: Router,
  private fb: FormBuilder,
  private adminService: AdminService,
  private equipeService: EquipeService,
  private agentService: AgentService
  ) {
    this.editForm = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(2)]],
      linkPowerBi: ['', [
        Validators.required,
        Validators.pattern(/^https:\/\/app\.powerbi\.com\/view\?r=.*/i)
      ]]
    });
  }

  ngOnInit(): void {
    this.checkLoginStatus();

    if (!this.isLoggedIn) {
      this.router.navigate(['/login']);
      return;
    }

    this.initializeWelcomeOverlay();
    this.verificarAdminSistema();
    this.carregarEquipesMenu();
    this.loadDashboards();
    this.carregarPaineisIa();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pararPolling();
    this.clearWelcomeTimers();
    if (this.agentBubbleTimer) {
      window.clearTimeout(this.agentBubbleTimer);
    }
    if (this.footerSignatureTimer) {
      window.clearTimeout(this.footerSignatureTimer);
    }
  }

  trackById(index: number, item: PainelDTO) {
    return item.id;
  }
    get nomesExistentes(): string[] {
    return (this.dashboards ?? []).map(p => p.nome ?? '');
  }
  // -------------------------
  // AUTH HELPERS
  // -------------------------
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

  get equipeMenuLabel(): string {
    return this.equipeSelecionada?.nome || 'Minha Equipe';
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

  revelarAssinaturaFooter(): void {
    this.showFooterSignature = true;
    if (this.footerSignatureTimer) {
      window.clearTimeout(this.footerSignatureTimer);
    }
    this.footerSignatureTimer = window.setTimeout(() => {
      this.showFooterSignature = false;
    }, 7000);
  }

  private toEquipeMenuItem(equipe: Equipe): EquipeMenuItem | null {
    if (!equipe.id) {
      return null;
    }

    const role = (equipe.role || 'VIEWER').toUpperCase();
    const roleNormalizado: UserRole =
      role === 'ADMIN' || role === 'EDITOR' || role === 'VIEWER' ? role : 'VIEWER';

    return {
      id: equipe.id,
      nome: equipe.nome,
      role: roleNormalizado
    };
  }

  private salvarEquipeSelecionada(equipe: EquipeMenuItem): void {
    const key = this.getEquipeStorageKey();
    if (!key) return;
    localStorage.setItem(key, JSON.stringify(equipe));
  }

  carregarEquipesMenu(): void {
    this.equipeService.listarMinhasEquipes().subscribe({
      next: (equipes) => {
        this.equipesMenu = (equipes || [])
          .map((equipe) => this.toEquipeMenuItem(equipe))
          .filter((equipe): equipe is EquipeMenuItem => !!equipe)
          .sort((a, b) => a.nome.localeCompare(b.nome));

        if (this.equipeSelecionada && !this.equipesMenu.some((e) => e.id === this.equipeSelecionada?.id)) {
          this.equipeSelecionada = null;
          this.currentRole = 'VIEWER';
        }
      },
      error: () => {
        this.equipesMenu = [];
      }
    });
  }

  selecionarEquipeDoMenu(equipe: EquipeMenuItem): void {
    this.equipeSelecionada = equipe;
    this.currentRole = equipe.role;
    this.salvarEquipeSelecionada(equipe);
    this.isAddOpen = false;
    this.loadDashboards();
    this.startPolling();
  }

    private getUserRoleFromEquipe(): UserRole {
      const role = (this.equipeSelecionada?.role || 'VIEWER').toUpperCase();
      if (role === 'VIEWER' || role === 'EDITOR' || role === 'ADMIN') {
        return role;
      }
      return 'VIEWER';
  }

  private getEquipeStorageKey(): string | null {
    const user = this.getUserFromStorage();
    if (!user?.id) return null;
    return `${HomeComponent.SELECTED_EQUIPE_KEY}:${user.id}`;
  }

  private loadEquipeSelecionada(): void {
    const key = this.getEquipeStorageKey();
    if (!key) {
      this.equipeSelecionada = null;
      this.currentRole = 'VIEWER';
      return;
    }
    const raw = localStorage.getItem(key);
    if (!raw) {
      this.equipeSelecionada = null;
      this.currentRole = 'VIEWER';
      return;
    }

      try {
        const equipe = JSON.parse(raw) as Partial<EquipeSelecionada>;
        if (!equipe.id || !equipe.nome) {
          this.equipeSelecionada = null;
          this.currentRole = 'VIEWER';
          return;
        }

        const role = (equipe.role || 'VIEWER').toUpperCase() as UserRole;
        this.equipeSelecionada = {
          id: equipe.id,
          nome: equipe.nome,
          role: (role === 'VIEWER' || role === 'EDITOR' || role === 'ADMIN') ? role : 'VIEWER'
        };
        this.currentRole = this.getUserRoleFromEquipe();
      } catch {
        this.equipeSelecionada = null;
        this.currentRole = 'VIEWER';
      }
    }

  private getUserFromStorage(): UsuarioLocalStorage | null {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;

    try {
      return JSON.parse(userStr) as UsuarioLocalStorage;
    } catch {
      return null;
    }
  }

  private handleAuthError(err: any) {
    if (err?.status === 401) {
      this.pararPolling();
      this.logout();
      return true;
    }
    if (err?.status === 403) {
      this.error = 'Permissão insuficiente para esta ação na equipe selecionada.';
      return true;
    }
    return false;
  }

  // -------------------------
  // Listagem
  // -------------------------
  loadDashboards(): void {
    this.loadEquipeSelecionada();

    const apiUrl = this.apiUrl;
    if (!apiUrl) {
      this.loading = false;
      this.dashboards = [];
      this.error = 'Selecione uma equipe em "Minha Equipe" para visualizar os painéis.';
      return;
    }

    this.loading = true;
    this.error = null;

    this.http.get<PainelDTO[]>(apiUrl).subscribe({
      next: (data) => {
        this.processarDadosRecebidos(data);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        if (this.handleAuthError(err)) return;
        this.error = 'Erro ao carregar painéis.';
      }
    });
  }

  /**
   * ✅ PROFISSIONAL:
   * - recalcula Authorization a cada tick
   * - trata erro sem matar stream
   * - evita flood 401/403
   */
  private startPolling(): void {
    this.pararPolling();

    const apiUrl = this.apiUrl;
    if (!apiUrl) {
      return;
    }

    this.pollingSub = interval(5000).pipe(
      switchMap(() => {
        return this.http.get<PainelDTO[]>(apiUrl).pipe(
          catchError((err) => {
            this.handleAuthError(err);
            return of(null);
          })
        );
      })
    ).subscribe((data) => {
      if (!data) return;
      this.processarDadosRecebidos(data);
    });
  }

  private pararPolling(): void {
    if (this.pollingSub) this.pollingSub.unsubscribe();
    this.pollingSub = undefined;
  }

  private processarDadosRecebidos(data: PainelDTO[]) {
    const antigos = new Map(this.dashboards.map(p => [p.id, p]));

    this.dashboards = (data || []).map((novo) => {
      const antigo = antigos.get(novo.id);

      if (novo.imagemCapaUrl) {
        const serverUrl = novo.imagemCapaUrl;
        const estavaCarregada = !!antigo?.carregada;
        const previewAnterior = antigo?.previewSrc;

        // Se a imagem ainda não carregou (ou falhou), use sempre a URL mais recente do servidor
        // (signed URLs podem mudar; não queremos ficar "presos" em uma URL inválida/expirada).
        const precisaAtualizarPreview = !estavaCarregada || !previewAnterior || previewAnterior !== serverUrl;
        const previewSrc = precisaAtualizarPreview ? serverUrl : previewAnterior;
        const carregada = precisaAtualizarPreview ? false : estavaCarregada;

        const pronto: PainelDTO = { ...novo, previewSrc, carregada };
        if (!carregada) {
          this.preloadImageForPainel(pronto);
        }

        return pronto;
      }

      return { ...novo, carregada: false, previewSrc: antigo?.previewSrc };
    });
  }

  private preloadImageForPainel(painel: PainelDTO) {
    if (!painel.imagemCapaUrl) return;

    const img = new Image();
    img.onload = () => {
      painel.previewSrc = painel.imagemCapaUrl || undefined;
      painel.carregada = true;
    };
    img.onerror = () => {
      painel.carregada = false;
    };
    img.src = painel.imagemCapaUrl;
  }

  onImageLoad(painel: PainelDTO) {
    painel.carregada = true;
  }

  onImageError(painel: PainelDTO) {
    painel.carregada = false;
  }

  // -------------------------
  // Painéis de IA (gerados no chat do proIAp e salvos por lá)
  // -------------------------
  carregarPaineisIa(): void {
    this.loadingPaineisIa = true;
    this.agentService.listarPaineisIa().subscribe({
      next: (paineis) => {
        this.paineisIa = paineis || [];
        this.loadingPaineisIa = false;
      },
      error: () => {
        this.paineisIa = [];
        this.loadingPaineisIa = false;
      }
    });
  }

  pedirConfirmacaoExclusaoPainelIa(id: number, ev?: MouseEvent): void {
    if (ev) {
      ev.preventDefault();
      ev.stopPropagation();
    }
    this.confirmandoExclusaoPainelIaId = id;
  }

  cancelarExclusaoPainelIa(ev?: MouseEvent): void {
    if (ev) {
      ev.preventDefault();
      ev.stopPropagation();
    }
    this.confirmandoExclusaoPainelIaId = null;
  }

  confirmarExclusaoPainelIa(id: number, ev?: MouseEvent): void {
    if (ev) {
      ev.preventDefault();
      ev.stopPropagation();
    }
    this.excluindoPainelIaId = id;
    this.agentService.excluirPainelIa(id).subscribe({
      next: () => {
        this.paineisIa = this.paineisIa.filter(p => p.id !== id);
        this.excluindoPainelIaId = null;
        this.confirmandoExclusaoPainelIaId = null;
      },
      error: () => {
        this.excluindoPainelIaId = null;
        this.confirmandoExclusaoPainelIaId = null;
      }
    });
  }

  // -------------------------
  // ✅ ADD (POPUP COMPONENT)
  // -------------------------
  abrirAdicionar(): void {
    if (!this.canEdit) {
      this.error = 'Permissão insuficiente: somente EDITOR e ADMIN podem criar painéis.';
      return;
    }
    if (!this.apiUrl) {
      this.error = 'Selecione uma equipe antes de criar um painel.';
      return;
    }
    this.isAddOpen = true;
  }

  fecharAdicionar(): void {
    this.isAddOpen = false;
  }

  onPainelSalvo(_: PainelDTO): void {
    // você pode otimizar e só inserir no começo,
    // mas loadDashboards garante consistência (statusCaptura etc.)
    this.isAddOpen = false;
    this.loadDashboards();
  }

  // -------------------------
  // CRUD: DELETE
  // -------------------------
  abrirExcluir(painel: PainelDTO, ev?: MouseEvent) {
    if (!this.canEdit) return;
    if (ev) {
      ev.preventDefault();
      ev.stopPropagation();
    }

    if (this.isEditOpen) {
      this.fecharEdicao();
    }

    this.deleteError = null;
    this.deleting = false;
    this.deleteMessage = null;
    this.deleteSuccess = false;
    this.deletingPainel = painel;
    this.isDeleteOpen = true;
  }

  fecharExcluir() {
    this.isDeleteOpen = false;
    this.deleting = false;
    this.deleteError = null;
    this.deleteMessage = null;
    this.deleteSuccess = false;
    this.deletingPainel = null;
  }

  confirmarExclusao() {
    if (!this.deletingPainel) return;

    const apiUrl = this.apiUrl;
    if (!apiUrl) {
      this.deleting = false;
      this.deleteError = 'Selecione uma equipe para excluir o painel.';
      return;
    }

    this.deleting = true;
    this.deleteError = null;

    this.http.delete(`${apiUrl}/${this.deletingPainel.id}`).subscribe({
      next: () => {
        this.dashboards = this.dashboards.filter(p => p.id !== this.deletingPainel!.id);
        this.deleting = false;
        this.deleteSuccess = true;
        this.deleteMessage = 'Painel excluído com sucesso.';
        setTimeout(() => this.fecharExcluir(), 1800);
      },
      error: (err) => {
        if (this.handleAuthError(err)) {
          this.deleting = false;
          this.deleteError = 'Permissão insuficiente para excluir painel nesta equipe.';
          this.fecharExcluir();
          return;
        }
        this.deleting = false;
        this.deleteError = 'Falha ao excluir painel.';
        this.deleteSuccess = false;
        this.deleteMessage = 'Não foi possível excluir o painel.';
      }
    });
  }

  // -------------------------
  // CRUD: EDIT (MODAL)
  // -------------------------
  abrirEdicao(painel: PainelDTO, ev: MouseEvent) {
    if (!this.canEdit) return;
    ev.preventDefault();
    ev.stopPropagation();

    this.editError = null;
    this.savingEdit = false;
    this.editingPainel = painel;
    this.isEditOpen = true;

    this.editForm.reset({
      nome: painel.nome ?? '',
      linkPowerBi: painel.linkPowerBi ?? ''
    });
  }

  fecharEdicao() {
    this.isEditOpen = false;
    this.editError = null;
    this.savingEdit = false;
    this.editingPainel = null;
    this.editForm.reset();
  }

  salvarEdicao() {
    if (!this.editingPainel) return;

    const apiUrl = this.apiUrl;
    if (!apiUrl) {
      this.editError = 'Selecione uma equipe para editar o painel.';
      return;
    }

    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    this.savingEdit = true;
    this.editError = null;

    const nomeNovo = String(this.editForm.value.nome || '').trim();
    const linkNovo = String(this.editForm.value.linkPowerBi || '').trim();

    const nomeAtual = (this.editingPainel.nome || '').trim();
    const linkAtual = (this.editingPainel.linkPowerBi || '').trim();

    const payload: any = {};

    if (nomeNovo && nomeNovo !== nomeAtual) {
      payload.nome = nomeNovo;
    }

    if (linkNovo && linkNovo !== linkAtual) {
      const prefixo = 'https://app.powerbi.com/view?r=';
      if (!linkNovo.startsWith(prefixo)) {
        this.editError = `Link inválido. O link deve começar com: ${prefixo}`;
        this.savingEdit = false;
        return;
      }
      payload.linkPowerBi = linkNovo;
    }

    if (Object.keys(payload).length === 0) {
      this.savingEdit = false;
      this.fecharEdicao();
      return;
    }

    this.http.put<PainelDTO>(`${apiUrl}/${this.editingPainel.id}`, payload)
      .subscribe({
        next: (atualizado) => {
          this.dashboards = this.dashboards.map(p =>
            p.id === this.editingPainel!.id
              ? { ...p, ...atualizado }
              : p
          );

          this.savingEdit = false;
          this.fecharEdicao();
        },
        error: (err) => {
          if (this.handleAuthError(err)) {
            if (err?.status === 403) {
              this.editError = 'Permissão insuficiente para editar painel nesta equipe.';
            }
            this.savingEdit = false;
            return;
          }

          if (err?.status === 409) {
            this.editError = 'Você já possui este painel cadastrado (link duplicado).';
          } else if (err?.status === 400) {
            this.editError = err?.error?.message || 'Link inválido.';
          } else if (err?.error?.message) {
            this.editError = err.error.message;
          } else {
            this.editError = 'Falha ao salvar alterações.';
          }

          this.savingEdit = false;
        }
      });
  }

  onOverlayClick(ev: MouseEvent) {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharEdicao();
    }
  }

  onDeleteOverlayClick(ev: MouseEvent) {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharExcluir();
    }
  }

  // -------------------------
  // Auth local
  // -------------------------
  checkLoginStatus(): void {
    const user = this.getUserFromStorage();

    if (!user?.token) {
      this.isLoggedIn = false;
      this.userName = null;
      this.currentRole = 'VIEWER';
      return;
    }

    this.isLoggedIn = true;
    this.userName = user.username || 'Usuario';
    this.loadEquipeSelecionada();
  }

  private initializeWelcomeOverlay(): void {
    const key = this.getWelcomeStorageKey();
    this.showWelcomeOverlay = localStorage.getItem(key) !== '1';
    this.welcomeStep = 0;
  }

  private getWelcomeStorageKey(): string {
    const user = this.getUserFromStorage();
    const userKey = user?.id || user?.email || user?.username || 'default';
    return `bicentral_welcome_seen_${userKey}`;
  }

  closeWelcomeOverlay(): void {
    this.clearWelcomeTimers();
    this.isWelcomeClosing = false;
    this.showWelcomeOverlay = false;
    localStorage.setItem(this.getWelcomeStorageKey(), '1');
  }

  nextWelcomeStep(): void {
    if (this.isWelcomeClosing) return;

    if (this.welcomeStep >= this.welcomeSlides.length - 1) {
      this.playWelcomeOutro();
      return;
    }

    this.welcomeStep += 1;
  }

  previousWelcomeStep(): void {
    if (this.welcomeStep <= 0) return;
    this.welcomeStep -= 1;
  }

  goToWelcomeStep(index: number): void {
    if (this.isWelcomeClosing) return;
    if (index < 0 || index >= this.welcomeSlides.length) return;
    this.welcomeStep = index;
  }

  private playWelcomeOutro(): void {
    this.clearWelcomeTimers();
    this.isWelcomeClosing = true;
    this.welcomeOutroTitle = 'Que bom ter voce aqui.';
    this.welcomeOutroSubtitle = 'Preparando seu espaco para organizar paineis...';

    this.welcomeTimers.push(
      window.setTimeout(() => {
        this.welcomeOutroTitle = 'Tudo pronto!';
        this.welcomeOutroSubtitle = 'Agora voce ja pode gerenciar seus paineis.';
      }, 1100)
    );

    this.welcomeTimers.push(
      window.setTimeout(() => {
        this.closeWelcomeOverlay();
      }, 2600)
    );
  }

  private clearWelcomeTimers(): void {
    this.welcomeTimers.forEach((timer) => window.clearTimeout(timer));
    this.welcomeTimers = [];
  }

  logout(): void {
    this.pararPolling();
    const equipeKey = this.getEquipeStorageKey();
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    if (equipeKey) localStorage.removeItem(equipeKey);
    localStorage.removeItem(HomeComponent.SELECTED_EQUIPE_KEY); // limpeza da chave antiga não escopada
    this.isLoggedIn = false;
    this.userName = null;
    this.equipeSelecionada = null;
    this.currentRole = 'VIEWER';
    this.router.navigate(['/login']);
  }
}
