import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-esqueci-senha',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './esqueci-senha.component.html',
  styleUrls: ['./esqueci-senha.component.css']
})
export class EsqueciSenhaComponent {
  email = '';
  carregando = false;
  enviado = false;
  message: string | null = null;

  constructor(private http: HttpClient) {}

  enviar(): void {
    const emailLimpo = this.email.trim();
    if (this.carregando || !emailLimpo) return;

    this.carregando = true;
    this.message = null;

    this.http.post<{ mensagem?: string }>('/api/usuarios/esqueci-senha', { email: emailLimpo })
      .subscribe({
        next: (response) => {
          this.carregando = false;
          this.enviado = true;
          this.message = response?.mensagem
            || 'Se esse e-mail tiver uma conta no BICentral, você vai receber um link de redefinição em instantes.';
        },
        error: () => {
          // Erro de verdade (rede/servidor) — aqui sim mostramos um erro real. A mensagem
          // "genérica por privacidade" só se aplica à resposta de SUCESSO do backend, que já
          // é a mesma esteja o e-mail cadastrado ou não.
          this.carregando = false;
          this.message = 'Não foi possível processar seu pedido agora. Tente novamente em instantes.';
        }
      });
  }
}
