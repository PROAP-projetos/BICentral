import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-redefinir-senha',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './redefinir-senha.component.html',
  styleUrls: ['./redefinir-senha.component.css']
})
export class RedefinirSenhaComponent implements OnInit {
  token = '';
  tokenAusente = false;

  novaSenha = '';
  confirmarSenha = '';
  mostrarSenha = false;
  mostrarConfirmarSenha = false;

  carregando = false;
  concluido = false;
  message: string | null = null;

  requisitosSenha = {
    tamanho: false,
    letra: false,
    numero: false,
    especial: false
  };

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router
  ) { }

  ngOnInit(): void {
    // Link veio sem token — não tem como redefinir nada, avisa em vez de deixar
    // a pessoa preencher tudo e só descobrir o problema ao enviar.
    const tokenNaUrl = this.route.snapshot.queryParamMap.get('token');
    if (!tokenNaUrl) {
      this.tokenAusente = true;
      return;
    }
    this.token = tokenNaUrl;
  }

  alternarMostrarSenha(): void {
    this.mostrarSenha = !this.mostrarSenha;
  }

  alternarMostrarConfirmarSenha(): void {
    this.mostrarConfirmarSenha = !this.mostrarConfirmarSenha;
  }

  atualizarRequisitosSenha(): void {
    const senha = this.novaSenha;
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

  redefinir(): void {
    if (this.carregando) return;

    if (!this.senhaAtendeRequisitos) {
      this.message = 'A senha precisa ter no mínimo 8 caracteres, com letra, número e caractere especial.';
      return;
    }

    if (this.novaSenha !== this.confirmarSenha) {
      this.message = 'As senhas digitadas não são iguais.';
      return;
    }

    this.carregando = true;
    this.message = null;

    this.http.post<{ mensagem?: string }>('/api/usuarios/redefinir-senha', {
      token: this.token,
      novaSenha: this.novaSenha
    }).subscribe({
      next: (response) => {
        this.carregando = false;
        this.concluido = true;
        this.message = response?.mensagem || 'Senha redefinida com sucesso!';
        setTimeout(() => this.router.navigate(['/login']), 3000);
      },
      error: (error) => {
        this.carregando = false;
        this.message = this.extrairMensagemErro(error);
      }
    });
  }

  private extrairMensagemErro(error: any): string {
    const backendError = error?.error;

    if (typeof backendError === 'string' && backendError.trim()) {
      return backendError;
    }

    if (backendError?.mensagem) {
      return backendError.mensagem;
    }

    if (error?.status === 404 || error?.status === 410) {
      return 'Esse link de redefinição não é mais válido. Peça um novo.';
    }

    return 'Não foi possível redefinir a senha agora. Tente novamente.';
  }
}
