import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    RouterLink
  ],
  templateUrl: './login.component.html'
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
      .subscribe((response: any) => {
        // Assuming the backend returns the user object on successful login
        localStorage.setItem('user', JSON.stringify(response));
        this.router.navigate(['/dashboard']);
      }, error => {
        this.message = 'Credenciais inválidas. Tente novamente.';
        console.error('Erro ao fazer login', error);
      });
  }
}