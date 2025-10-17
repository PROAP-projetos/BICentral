import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    RouterLink
  ],
  templateUrl: './cadastro.component.html'
})
export class CadastroComponent {

  usuario = {
    username: '',
    email: '',
    password: ''
  };
  message: string | null = null;
  registrationSuccess = false;

  constructor(private http: HttpClient, private router: Router) { }

  cadastrar() {
    this.http.post('http://localhost:8080/api/auth/cadastro', this.usuario)
      .subscribe(response => {
        this.registrationSuccess = true;
        this.message = 'Cadastro realizado com sucesso! Por favor, verifique seu e-mail para ativar sua conta.';
      }, error => {
        if (error.status === 409) {
          this.message = error.error;
        } else if (error.status === 400) {
          this.message = Object.values(error.error).join(', ');
        }
        console.error('Erro ao cadastrar usuário', error);
      });
  }
}
