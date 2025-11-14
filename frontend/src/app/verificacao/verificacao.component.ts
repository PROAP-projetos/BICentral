import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-verificacao',
  standalone: true,
  imports: [CommonModule, HttpClientModule],
  templateUrl: './verificacao.component.html',
  styleUrls: ['./verificacao.component.css']
})
export class VerificacaoComponent implements OnInit {

  message: string | null = null;
  verificationSuccess = false;

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private router: Router
  ) { }

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) {
      this.http.get(`http://localhost:8080/api/auth/verify?code=${code}`, { responseType: 'text' })
        .subscribe(() => {
          this.verificationSuccess = true;
          this.message = 'Sua conta foi verificada com sucesso! Você será redirecionado para o login em 5 segundos.';
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 5000);
        }, () => {
          this.verificationSuccess = false;
          this.message = 'Ocorreu um erro ao verificar sua conta. Por favor, tente novamente.';
        });
    }
  }
}
