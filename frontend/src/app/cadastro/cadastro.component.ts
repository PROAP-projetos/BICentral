import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    RouterLink
  ],
  templateUrl: './cadastro.component.html',
  styleUrl: './cadastro.component.css'
})
export class CadastroComponent {

  usuario = {
    username: '',
    email: '',
    password: ''
  };

  constructor(private http: HttpClient) { }

  cadastrar() {
    this.http.post('http://localhost:8080/api/usuarios/cadastro', this.usuario)
      .subscribe(response => {
        console.log('Usuário cadastrado com sucesso!', response);
      }, error => {
        console.error('Erro ao cadastrar usuário', error);
      });
  }
}
