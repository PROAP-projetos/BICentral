import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';

// Define a estrutura do objeto que vem do Spring Boot
interface PainelDTO {
  nome: string;
  linkPowerBi: string;
  imagemCapaBase64: string;
}

@Component({
  selector: 'app-home', 
  standalone: true, 
  imports: [CommonModule, HttpClientModule], 
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {

  // --- LÓGICA DE LISTAGEM DE PAINÉIS ---
  // A lista agora conterá apenas o resultado do scraper
  dashboards: PainelDTO[] = []; 
  loading: boolean = true;
  error: string | null = null;
  
  // URL CORRIGIDA: Aponta para o endpoint de teste único
  private API_URL = '/api/paineis/teste-scraper'; 

  // --- LÓGICA DE CONTROLE DE MENU ---
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

    // A chamada espera AGORA um ÚNICO PainelDTO
    this.http.get<PainelDTO>(this.API_URL).subscribe({
      next: (data) => {
        // Se a chamada for bem-sucedida, adicionamos o objeto único à lista
        if (data && data.imagemCapaBase64 !== null) {
          this.dashboards = [data]; // Transforma o objeto único em uma lista de um item
        } else {
          // Se o Base64 for null (scraper falhou), mostra erro
          this.error = 'O painel foi encontrado, mas o Web Scraper falhou em capturar a capa.';
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Erro ao buscar painel:', err);
        // Exibe o erro de comunicação
        this.error = 'Não foi possível comunicar com o Spring Boot. Verifique o console.';
        this.loading = false;
      }
    });
  }
}
