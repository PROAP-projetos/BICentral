import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  credentials = { email: '', password: '' };
  message: string | null = null;
  messageType: 'error' | 'success' | 'info' = 'error';

  constructor(private http: HttpClient, private router: Router) { }

  login() {
    this.message = null;
    console.log('Iniciando tentativa de login...');
    this.http.post('/api/usuarios/login', this.credentials)
      .subscribe({
        next: (response: any) => {
          console.log('Resposta do Backend recebida:', response);

          if (response && response.token) {
            // Limpa apenas resíduos de autenticação anteriores
            localStorage.removeItem('token');
            localStorage.removeItem('user');

            // 1. Salva o token puro (para uso do interceptor)
            localStorage.setItem('token', response.token);

            // 2. Salva o objeto do usuário (agora incluindo o token para o HomeComponent)
            localStorage.setItem('user', JSON.stringify({
              id: response.id,
              username: response.username,
              token: response.token
            }));

            // Tester do proIAp cai direto no agente em vez da Home (é pra isso que ela está aqui).
            const destino = response.tester ? '/agente' : '/';
            console.log(`Dados salvos no localStorage. Redirecionando para ${destino}...`);

            // 3. Força a navegação e verifica se ela ocorreu
            this.router.navigate([destino]).then(success => {
              if (success) {
                console.log('Navegação concluída com sucesso!');
              } else {
                console.error(`Falha na navegação. Verifique se a rota "${destino}" existe.`);
              }
            });
          } else {
            console.warn('Backend respondeu, mas sem o campo token esperado.');
          }
        },
        error: (error) => {
          this.messageType = 'error';
          this.message = this.extractErrorMessage(error);
          console.error('Erro detalhado no login:', error);
        }
      });
  }

  private extractErrorMessage(error: any): string {
    const backendError = error?.error;

    if (typeof backendError === 'string' && backendError.trim()) {
      return backendError;
    }

    if (backendError?.mensagem) {
      return backendError.mensagem;
    }

    if (backendError?.message) {
      return backendError.message;
    }

    if (error?.status === 401 || error?.status === 403) {
      return 'Email ou senha inválidos.';
    }

    return 'Erro ao conectar com o servidor.';
  }
}
