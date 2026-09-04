import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

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
  styleUrls: ['./cadastro.component.css']
})
export class CadastroComponent implements OnInit {

  usuario = {
    username: '',
    email: '',
    password: ''
  };
  message: string | null = null;
  messageType: 'error' | 'success' | 'info' = 'info';
  alertTitle = 'Atenção';
  showLoginShortcut = false;
  registrationSuccess = false;

  constructor(private http: HttpClient, private route: ActivatedRoute) { }

  ngOnInit(): void {
    // Vem de um link tipo /cadastro?email=fulano@uft.edu.br (ex: admin adicionando
    // um tester cujo e-mail ainda não tem conta) — só pré-preenche, não trava o campo.
    const emailNaUrl = this.route.snapshot.queryParamMap.get('email');
    if (emailNaUrl) {
      this.usuario.email = emailNaUrl;
    }
  }

  cadastrar() {
    this.message = null;
    this.messageType = 'info';
    this.alertTitle = 'Atenção';
    this.showLoginShortcut = false;

    this.http.post('/api/usuarios/cadastro', this.usuario)
      .subscribe({
        next: (response) => {
          this.registrationSuccess = true;
          this.message = 'Cadastro realizado com sucesso! Por favor, verifique seu e-mail para ativar sua conta.';
          this.messageType = 'success';
          this.alertTitle = 'Sucesso';
          this.showLoginShortcut = false;
        },
        error: (error) => {
          this.registrationSuccess = false;
          this.messageType = 'error';
          this.message = this.extractErrorMessage(error);
          const isDuplicate = this.isDuplicateAccountError(error);
          this.showLoginShortcut = isDuplicate;
          this.alertTitle = isDuplicate ? 'Conta já existente' : 'Atenção';
          console.error('Erro ao cadastrar usuário', error);
        }
      });
  }

  private extractErrorMessage(error: any): string {
    const backendError = error?.error;
    if (this.isDuplicateAccountError(error)) {
      return 'Este e-mail já está cadastrado. Faça login para continuar.';
    }

    if (typeof backendError === 'string' && backendError.trim()) {
      return backendError;
    }

    if (backendError?.mensagem) {
      return backendError.mensagem;
    }

    if (backendError?.message) {
      return backendError.message;
    }

    if (error?.status === 400 && backendError && typeof backendError === 'object') {
      const values = Object.values(backendError)
        .map((value) => String(value))
        .filter((value) => value.trim().length > 0);

      if (values.length > 0) {
        return values.join(', ');
      }
    }

    if (error?.status === 409) {
      return 'Já existe uma conta com este e-mail.';
    }

    return 'Ocorreu um erro ao tentar cadastrar. Por favor, tente novamente mais tarde.';
  }

  private isDuplicateAccountError(error: any): boolean {
    const payload = error?.error;
    const rawMessage = [
      typeof payload === 'string' ? payload : '',
      payload?.mensagem ?? '',
      payload?.message ?? ''
    ]
      .join(' ')
      .toLowerCase();

    const isEmailDuplicate =
      (rawMessage.includes('email') || rawMessage.includes('e-mail')) &&
      (rawMessage.includes('já existe') || rawMessage.includes('já está cadastrado') || rawMessage.includes('em uso'));

    if (isEmailDuplicate) {
      return true;
    }

    return error?.status === 409 && rawMessage.length === 0;
  }
}
