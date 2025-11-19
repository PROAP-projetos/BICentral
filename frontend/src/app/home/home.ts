import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';

// Estrutura correta do objeto que vem do backend
interface PainelDTO {
nome: string;
linkPowerBi: string;
imagemCapaUrl: string;
statusCaptura: string;
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

isMenuOpen: boolean = false;

toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  constructor(private http: HttpClient) { }

  ngOnInit(): void {
    this.loadDashboards();
  }

  loadDashboards(): void {
    this.loading = true;
    this.error = null;

    this.http.get<PainelDTO[]>(this.API_URL).subscribe({
      next: (data) => {
        this.dashboards = data ?? [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erro ao buscar painéis:', err);
        this.error = 'Não foi possível carregar os painéis.';
        this.loading = false;
      }
    });
  }
}
