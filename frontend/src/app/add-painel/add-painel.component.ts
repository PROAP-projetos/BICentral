import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

interface AddPainel {
id?: number;
nome: string;
linkPowerBi: string;
usuario?: { id: number };
}

@Component({
selector: 'app-add-painel',
standalone: true,
imports: [FormsModule, CommonModule],
templateUrl: './add-painel.component.html',
styleUrls: ['./add-painel.component.css']
})
export class AddPainelComponent implements OnInit {

painel: AddPainel = {
nome: '',
linkPowerBi: ''
};

// Variáveis para o sistema de aviso de boas práticas
nomesExistentes: string[] = [];
isNomeRepetido: boolean = false;

mensagem: string = '';
erro: boolean = false;
carregando: boolean = false;

private API_URL = 'http://localhost:8080/api/paineis';

constructor(private http: HttpClient, public router: Router) { }

  ngOnInit(): void {
    this.carregarNomesExistentes();
  }

  /**
   * Busca os painéis atuais para validar nomes repetidos como boa prática.
   * Usamos o endpoint 'com-capa' que já retorna os dados do usuário logado.
   */
  private carregarNomesExistentes() {
    this.http.get<any[]>('http://localhost:8080/api/paineis/com-capa').subscribe({
      next: (data) => {
        this.nomesExistentes = data.map(p => p.nome.toLowerCase().trim());
      },
      error: (err) => console.error('Não foi possível carregar nomes para validação:', err)
    });
  }

  /**
   * Disparado a cada tecla digitada no campo Nome.
   */
  validarNomeUnico() {
    if (!this.painel.nome) {
      this.isNomeRepetido = false;
      return;
    }
    const nomeAtual = this.painel.nome.toLowerCase().trim();
    this.isNomeRepetido = this.nomesExistentes.includes(nomeAtual);
  }

  salvarPainel() {
    const userJson = localStorage.getItem('user');
    if (!userJson) {
      this.erro = true;
      this.mensagem = "Sua sessão expirou. Por favor, faça login novamente.";
      return;
    }

    const user = JSON.parse(userJson);
    this.carregando = true;
    this.mensagem = '';
    this.erro = false;

    const payload = {
      nome: this.painel.nome.trim(),
      linkPowerBi: this.painel.linkPowerBi.trim(),
      usuario: { id: user.id }
    };

    this.http.post(this.API_URL, payload).subscribe({
      next: () => {
        this.erro = false;
        this.carregando = false;
        this.mensagem = "Painel cadastrado com sucesso! Redirecionando...";
        setTimeout(() => this.router.navigate(['/']), 1500);
      },
      error: (e: HttpErrorResponse) => {
        this.carregando = false;
        this.erro = true;

        if (e.status === 409) {
          this.mensagem = e.error?.mensagem || "Você já possui este painel cadastrado na sua lista.";
        }
        else if (e.status === 400 && e.error?.mensagem) {
          this.mensagem = e.error.mensagem;
        }
        else if (e.status === 403 || e.status === 401) {
          this.mensagem = "Não foi possível autorizar o cadastro. Tente sair e entrar novamente.";
        }
        else if (e.status === 0) {
          this.mensagem = "O servidor não respondeu. Verifique sua internet.";
        }
        else {
          this.mensagem = "Ops! Tivemos um imprevisto técnico ao salvar seu painel.";
        }
        console.error('Erro detalhado:', e);
      }
    });
  }
}
