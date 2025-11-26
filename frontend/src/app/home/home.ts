import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';

interface PainelDTO {
nome: string;
linkPowerBi: string;
imagemCapaUrl: string;
statusCaptura: string;

// controle local
previewSrc?: string;   // só definida se a imagem existir
carregada?: boolean;   // true quando imagem válida e exibida
}

@Component({
selector: 'app-home',
standalone: true,
imports: [CommonModule, HttpClientModule],
templateUrl: './home.html',
styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {

dashboards: PainelDTO[] = [];
loading: boolean = true;
error: string | null = null;

private API_URL = '/api/paineis/com-capa';

constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadDashboards();
  }

  loadDashboards(): void {
    this.loading = true;
    this.error = null;

    this.http.get<PainelDTO[]>(this.API_URL).subscribe({
      next: (data) => {
        this.dashboards = (data ?? []).map(p => ({
          ...p,
          previewSrc: undefined,
          carregada: false
        }));

        // pré-carrega imagens (não insere <img> até confirmar sucesso)
        this.dashboards.forEach(p => this.preloadImageForPainel(p));

        this.loading = false;
      },
      error: (err) => {
        console.error('Erro ao buscar painéis:', err);
        this.error = 'Não foi possível carregar os painéis.';
        this.loading = false;
      }
    });
  }

  private preloadImageForPainel(painel: PainelDTO) {
    const url = painel.imagemCapaUrl;
    if (!url) {
      // sem URL: mostra o placeholder (não marca como "carregada")
      painel.previewSrc = undefined;
      painel.carregada = false;
      return;
    }

    const img = new Image();
    img.onload = () => {
      // imagem válida — agora permite que o <img> seja renderizado e esconde o placeholder
      painel.previewSrc = url;
      painel.carregada = true;
    };
    img.onerror = () => {
      // falha ao carregar → mantém o placeholder visível
      painel.previewSrc = undefined;
      painel.carregada = false;
    };

    // dispara o carregamento
    img.src = url;
  }

  // handlers caso o <img> precise deles depois
  onImageLoad(painel: PainelDTO) {
    painel.carregada = true;
  }
  onImageError(painel: PainelDTO) {
    painel.previewSrc = undefined;
    painel.carregada = false;
  }

  // ex: adicionar localmente um painel
  addLocalPainel(p: Partial<PainelDTO>) {
    const painel: PainelDTO = {
      nome: p.nome ?? 'Painel sem nome',
      linkPowerBi: p.linkPowerBi ?? '#',
      imagemCapaUrl: p.imagemCapaUrl ?? '',
      statusCaptura: p.statusCaptura ?? '',
      previewSrc: undefined,
      carregada: false
    };
    this.dashboards = [painel, ...this.dashboards];
    this.preloadImageForPainel(painel);
  }
}
