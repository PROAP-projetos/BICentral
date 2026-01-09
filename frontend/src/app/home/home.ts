import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';

interface PainelDTO {
id: any;
nome: string;
linkPowerBi: string;
imagemCapaUrl: string;
statusCaptura: string;
previewSrc?: string;
carregada?: boolean;
}

@Component({
selector: 'app-home',
standalone: true,
imports: [CommonModule],
templateUrl: './home.html',
styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit, OnDestroy {

dashboards: PainelDTO[] = [];
loading: boolean = true;
error: string | null = null;
isLoggedIn: boolean = false;
userName: string | null = null;

private pollingSub?: Subscription;
private API_URL = 'http://localhost:8080/api/paineis/com-capa';

constructor(private http: HttpClient, private router: Router) {}

  ngOnInit(): void {
    this.checkAuthStatus();
    this.loadDashboards();
    this.iniciarMonitoramentoDeImagens();
  }

  ngOnDestroy(): void {
    this.pararPolling();
  }

  private pararPolling() {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
    }
  }

  iniciarMonitoramentoDeImagens(): void {
    this.pararPolling();
    this.pollingSub = interval(5000).pipe(
      switchMap(() => this.http.get<PainelDTO[]>(this.API_URL)),
      takeWhile(paineis =>
        paineis.some(p => p.statusCaptura === 'PENDENTE' || p.statusCaptura === 'PROCESSANDO'),
        true
)
).subscribe({
      next: (data) => this.processarDadosRecebidos(data),
      error: (err) => {
        if (err.status === 403 || err.status === 401) this.logout();
      }
    });
  }

  loadDashboards(): void {
    this.loading = true;
    this.http.get<PainelDTO[]>(this.API_URL).subscribe({
      next: (data) => {
        this.processarDadosRecebidos(data);
        this.loading = false;
      },
      error: () => {
        this.error = 'Erro ao carregar os painéis da PROAP.';
        this.loading = false;
      }
    });
  }

  private processarDadosRecebidos(data: PainelDTO[]): void {
    if (!data || data.length === 0) {
      this.dashboards = [];
      return;
    }

    this.dashboards = data.map(novo => {
      // Se o backend não mandar ID, usamos o linkPowerBi como chave única para não repetir
      const identificadorNovo = novo.id || novo.linkPowerBi;

      const antigo = this.dashboards.find(d => (d.id || d.linkPowerBi) === identificadorNovo);

      // Se já estava carregado, mantém para não piscar
      if (antigo && antigo.carregada && antigo.statusCaptura === 'CONCLUIDA' && antigo.previewSrc) {
        return antigo;
      }

      // Se concluiu agora
      if (novo.statusCaptura === 'CONCLUIDA') {
        const pronto = {
          ...novo,
          previewSrc: novo.imagemCapaUrl,
          carregada: !!novo.imagemCapaUrl
        };
        if (!antigo || !antigo.previewSrc) {
          this.preloadImageForPainel(pronto);
        }
        return pronto;
      }

      return { ...novo, carregada: false };
    });
  }

  private preloadImageForPainel(painel: PainelDTO) {
    if (!painel.imagemCapaUrl) return;
    const img = new Image();
    img.onload = () => {
      painel.previewSrc = painel.imagemCapaUrl;
      painel.carregada = true;
    };
    img.src = painel.imagemCapaUrl;
  }

  onImageLoad(painel: PainelDTO) {
    painel.carregada = true;
  }

  onImageError(painel: PainelDTO) {
    painel.previewSrc = undefined;
    painel.carregada = false;
  }

  trackById(index: number, item: PainelDTO): any {
    return item.id || item.linkPowerBi; // Garante que o Angular identifique cada card individualmente
  }

  checkAuthStatus(): void {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        this.isLoggedIn = true;
        this.userName = user.username || 'Usuário';
      } catch (e) {
        this.logout();
      }
    }
  }

  logout(): void {
    this.pararPolling();
    localStorage.removeItem('user');
    this.isLoggedIn = false;
    this.userName = null;
    this.router.navigate(['/login']);
  }
}
