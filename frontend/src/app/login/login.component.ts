import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http'; // Removi o HttpClientModule daqui
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
selector: 'app-login',
standalone: true,
imports: [
CommonModule,
FormsModule,
RouterLink
],
templateUrl: './login.component.html',
styleUrls: ['./login.component.css']
})
export class LoginComponent {

credentials = {
email: '',
password: ''
};
message: string | null = null;

constructor(private http: HttpClient, private router: Router) { }

  login() {
    this.http.post('http://localhost:8080/api/usuarios/login', this.credentials)
      .subscribe({
        next: (response: any) => {
          console.log('Resposta do Backend:', response);

          if (response && response.token) {
            // Salva o objeto completo (Token, Username e ID)
            localStorage.setItem('user', JSON.stringify(response));

            // Pequeno delay para garantir que o browser gravou antes de mudar de página
            setTimeout(() => this.router.navigate(['/']), 100);
          }
        },
        error: (error) => {
          this.message = 'Credenciais inválidas. Tente novamente.';
          console.error('Erro ao fazer login', error);
        }
      });
  }
}
