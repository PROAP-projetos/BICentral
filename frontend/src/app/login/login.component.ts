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

  constructor(private http: HttpClient, private router: Router) { }

  login() {
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

            console.log('Dados salvos no localStorage. Redirecionando para a Home...');

            // 3. Força a navegação e verifica se ela ocorreu
            this.router.navigate(['/']).then(success => {
              if (success) {
                console.log('Navegação concluída com sucesso!');
              } else {
                console.error('Falha na navegação. Verifique se a rota "/" existe.');
              }
            });
          } else {
            console.warn('Backend respondeu, mas sem o campo token esperado.');
          }
        },
        error: (error) => {
          if (error.status === 401 || error.status === 403) {
            this.message = error.error || 'Email ou senha inválidos.';
          } else {
            this.message = 'Erro ao conectar com o servidor.';
          }
          console.error('Erro detalhado no login:', error);
        }
      });
  }
}
