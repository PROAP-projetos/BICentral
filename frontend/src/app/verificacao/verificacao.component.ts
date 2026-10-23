import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

type Estado = 'verificando' | 'sucesso' | 'erro' | 'sem-codigo';

@Component({
  selector: 'app-verificacao',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verificacao.component.html',
  styleUrls: ['./verificacao.component.css']
})
export class VerificacaoComponent implements OnInit, OnDestroy {

  estado: Estado = 'verificando';
  segundosParaLogin = 5;
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private router: Router
  ) { }

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (!code) {
      this.estado = 'sem-codigo';
      return;
    }

    this.http.get('/api/usuarios/verify', { params: { code }, responseType: 'text' })
      .subscribe({
        next: () => {
          this.estado = 'sucesso';
          this.iniciarContagem();
        },
        error: () => {
          this.estado = 'erro';
        }
      });
  }

  irParaLogin(): void {
    this.router.navigate(['/login']);
  }

  private iniciarContagem(): void {
    this.timer = setInterval(() => {
      this.segundosParaLogin--;
      if (this.segundosParaLogin <= 0) {
        this.pararContagem();
        this.irParaLogin();
      }
    }, 1000);
  }

  private pararContagem(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  ngOnDestroy(): void {
    this.pararContagem();
  }
}
