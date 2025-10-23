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
  
  // URL para buscar todos os painéis do banco de dados
  private API_URL = '/api/paineis/com-capa'; 

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

    // A chamada agora espera uma LISTA de PainelDTO
    this.http.get<PainelDTO[]>(this.API_URL).subscribe({
      next: (data) => {
        // Se a chamada for bem-sucedida, usa a lista completa
        if (data && data.length > 0) {
          this.dashboards = data; // Usa a lista completa de painéis
        } else {
          // Se não houver painéis cadastrados
          this.dashboards = [];
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Erro ao buscar painéis:', err);
        // Exibe o erro de comunicação
        this.error = 'Não foi possível carregar os painéis. Verifique se o backend está rodando.';
        this.loading = false;
      }
    });
  }
}
