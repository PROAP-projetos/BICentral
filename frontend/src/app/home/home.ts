import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { FormBuilder, ReactiveFormsModule, Validators, FormGroup } from '@angular/forms';

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
token?: string;
username?: string;
email?: string;
}

@Component({
selector: 'app-home',
standalone: true,
imports: [CommonModule, RouterModule, ReactiveFormsModule],
templateUrl: './home.html',
styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit, OnDestroy {

dashboards: PainelDTO[] = [];
loading = true;
error: string | null = null;

isLoggedIn = false;
userName: string | null = null;

private pollingSub?: Subscription;
private readonly API_URL = 'http://localhost:8080/api/paineis';

// -------------------------
// MODAL EDIÇÃO
// -------------------------
isEditOpen = false;
savingEdit = false;
editError: string | null = null;
editingPainel: PainelDTO | null = null;

// ✅ IMPORTANTE: não inicialize com this.fb aqui
editForm!: FormGroup;

constructor(
    private http: HttpClient,
    private router: Router,
    private fb: FormBuilder
  ) {
    // ✅ inicializa aqui (após DI existir)
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

    this.loadDashboards();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pararPolling();
  }

  trackById(index: number, item: PainelDTO) {
    return item.id;
  }

  // -------------------------
  // AUTH HELPERS
  // -------------------------
  private getUserFromStorage(): UsuarioLocalStorage | null {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;

    try {
      return JSON.parse(userStr) as UsuarioLocalStorage;
    } catch {
      return null;
    }
  }

  private getAuthHeaders(): HttpHeaders {
    const user = this.getUserFromStorage();
    const token = user?.token;

    if (!token) return new HttpHeaders();

    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  private handleAuthError(err: any) {
    if (err?.status === 401 || err?.status === 403) {
      this.logout();
      return true;
    }
    return false;
  }

  // -------------------------
  // Listagem
  // -------------------------
  loadDashboards(): void {
    this.loading = true;
    this.error = null;

    const headers = this.getAuthHeaders();

    this.http.get<PainelDTO[]>(this.API_URL, { headers }).subscribe({
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

  private startPolling(): void {
    const headers = this.getAuthHeaders();

    this.pollingSub = interval(5000)
      .pipe(switchMap(() => this.http.get<PainelDTO[]>(this.API_URL, { headers })))
      .subscribe({
        next: (data) => this.processarDadosRecebidos(data),
        error: (err) => {
          this.handleAuthError(err);
        }
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
        const pronto: PainelDTO = {
          ...novo,
          previewSrc: antigo?.previewSrc || novo.imagemCapaUrl,
          carregada: !!antigo?.carregada
        };

        if (!antigo || !antigo.previewSrc) {
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
  // CRUD: DELETE
  // -------------------------
  excluirPainel(painel: PainelDTO, ev: MouseEvent) {
    ev.preventDefault();
    ev.stopPropagation();

    const ok = confirm(`Excluir o painel "${painel.nome}"?`);
    if (!ok) return;

    const headers = this.getAuthHeaders();

    this.http.delete(`${this.API_URL}/${painel.id}`, { headers }).subscribe({
      next: () => {
        this.dashboards = this.dashboards.filter(p => p.id !== painel.id);
      },
      error: (err) => {
        if (this.handleAuthError(err)) return;
        alert('Falha ao excluir painel.');
      }
    });
  }

  // -------------------------
  // CRUD: EDIT (MODAL)
  // -------------------------
  abrirEdicao(painel: PainelDTO, ev: MouseEvent) {
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

    // só manda nome se mudou
    if (nomeNovo && nomeNovo !== nomeAtual) {
      payload.nome = nomeNovo;
    }

    // só manda link se mudou
    if (linkNovo && linkNovo !== linkAtual) {
      // validação do prefixo (mesma regra do backend)
      const prefixo = 'https://app.powerbi.com/view?r=';
      if (!linkNovo.startsWith(prefixo)) {
        this.editError = `Link inválido. O link deve começar com: ${prefixo}`;
        this.savingEdit = false;
        return;
      }

      payload.linkPowerBi = linkNovo;
    }

    // nada mudou -> só fecha
    if (Object.keys(payload).length === 0) {
      this.savingEdit = false;
      this.fecharEdicao();
      return;
    }

    const headers = this.getAuthHeaders();

    this.http.put<PainelDTO>(`${this.API_URL}/${this.editingPainel.id}`, payload, { headers })
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
          if (this.handleAuthError(err)) return;

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

  // fecha ao clicar fora do modal
  onOverlayClick(ev: MouseEvent) {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharEdicao();
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
      return;
    }

    this.isLoggedIn = true;
    this.userName = user.username || 'Usuário';
  }

  logout(): void {
    this.pararPolling();
    localStorage.removeItem('user');
    this.isLoggedIn = false;
    this.userName = null;
    this.router.navigate(['/login']);
  }
}
