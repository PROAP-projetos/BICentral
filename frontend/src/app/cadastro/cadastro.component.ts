import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
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
  confirmarSenha = '';
  message: string | null = null;
  messageType: 'error' | 'success' | 'info' = 'info';
  alertTitle = 'Atenção';
  showLoginShortcut = false;
  registrationSuccess = false;
  emailTravado = false;
  mostrarSenha = false;
  mostrarConfirmarSenha = false;

  requisitosSenha = {
    tamanho: false,
    letra: false,
    numero: false,
    especial: false
  };

  alternarMostrarSenha(): void {
    this.mostrarSenha = !this.mostrarSenha;
  }

  alternarMostrarConfirmarSenha(): void {
    this.mostrarConfirmarSenha = !this.mostrarConfirmarSenha;
  }

  atualizarRequisitosSenha(): void {
    const senha = this.usuario.password;
    this.requisitosSenha = {
      tamanho: senha.length >= 8,
      letra: /[a-zA-Z]/.test(senha),
      numero: /[0-9]/.test(senha),
      especial: /[^a-zA-Z0-9]/.test(senha)
    };
  }

  get senhaAtendeRequisitos(): boolean {
    const r = this.requisitosSenha;
    return r.tamanho && r.letra && r.numero && r.especial;
  }

  constructor(private http: HttpClient, private route: ActivatedRoute) { }

  ngOnInit(): void {
    // Vem de um link tipo /cadastro?email=fulano@uft.edu.br (convite de tester mandado pelo
    // admin) — trava o campo pra pessoa não trocar sem querer (ou de propósito) pra outro
    // e-mail que não foi convidado. O backend também recusa no fim, mas travar aqui evita
    // a pessoa preencher tudo e só descobrir o bloqueio depois de enviar.
    const emailNaUrl = this.route.snapshot.queryParamMap.get('email');
    if (emailNaUrl) {
      this.usuario.email = emailNaUrl;
      this.emailTravado = true;
    }
  }

  cadastrar() {
    this.message = null;
    this.messageType = 'info';
    this.alertTitle = 'Atenção';
    this.showLoginShortcut = false;

    if (!this.senhaAtendeRequisitos) {
      this.messageType = 'error';
      this.alertTitle = 'Atenção';
      this.message = 'A senha precisa ter no mínimo 8 caracteres, com letra, número e caractere especial.';
      return;
    }

    // Confere aqui pra evitar que um typo na senha (digitada só uma vez) crie uma conta
    // com senha diferente da que a pessoa acha que colocou, e ela não conseguir mais entrar.
    if (this.usuario.password !== this.confirmarSenha) {
      this.messageType = 'error';
      this.alertTitle = 'Atenção';
      this.message = 'As senhas digitadas não são iguais.';
      return;
    }

    this.http.post<{ mensagem?: string }>('/api/usuarios/cadastro', this.usuario)
      .subscribe({
        next: (response) => {
          this.registrationSuccess = true;
          // O backend já diferencia tester (pula verificação) de cadastro normal — usa a
          // mensagem dele em vez de um texto fixo que sempre manda "verifique seu e-mail".
          this.message = response?.mensagem || 'Cadastro realizado com sucesso!';
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
