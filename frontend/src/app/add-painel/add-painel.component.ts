import { Component, EventEmitter, Output, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface PainelDTO {
id: number;
nome: string;
linkPowerBi: string;
imagemCapaUrl: string | null;
statusCaptura: string;
}

interface UsuarioLocalStorage {
token?: string;
username?: string;
email?: string;
}

@Component({
selector: 'app-add-painel',
standalone: true,
imports: [CommonModule, FormsModule],
templateUrl: './add-painel.component.html',
styleUrls: ['./add-painel.component.css']
})
export class AddPainelComponent {

// ✅ Agora a Home pode passar a lista de nomes existentes pra validar duplicado
@Input() nomesExistentes: string[] = [];

@Output() fechar = new EventEmitter<void>();
@Output() salvo = new EventEmitter<PainelDTO>();

painel = {
nome: '',
linkPowerBi: ''
};

carregando = false;
mensagem: string | null = null;
erro = false;

// alerta visual (dica)
isNomeRepetido = false;

private readonly API_URL = 'http://localhost:8080/api/paineis';
private readonly POWERBI_PREFIX = 'https://app.powerbi.com/view?r=';

constructor(private http: HttpClient) {}

  // -------------------------
  // Modal helpers
  // -------------------------
  fecharModal(): void {
    this.fechar.emit();
  }

  onOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharModal();
    }
  }

  // -------------------------
  // Auth helpers
  // -------------------------
  private getUserFromStorage(): UsuarioLocalStorage | null {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;

    try {
      return JSON.parse(userStr) as UsuarioLocalStorage;
    } catch {
      return null;
    }
  }

  private getAuthHeaders(): HttpHeaders | null {
    const user = this.getUserFromStorage();
    const token = user?.token;

    if (!token) return null;

    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  // -------------------------
  // Helpers de validação/mensagem
  // -------------------------
  private limparMensagens(): void {
    this.mensagem = null;
    this.erro = false;
  }

  private setErro(msg: string): void {
    this.erro = true;
    this.mensagem = msg;
  }

  private setSucesso(msg: string): void {
    this.erro = false;
    this.mensagem = msg;
  }

  private normalizarCampo(valor: any): string {
    return String(valor ?? '').trim();
  }

  private normalizarComparacao(valor: any): string {
    return this.normalizarCampo(valor).toLowerCase();
  }

  private validarCampos(nome: string, linkPowerBi: string): string | null {
    if (!nome || nome.length < 3) return 'Informe um nome válido (mínimo 3 caracteres).';
    if (!linkPowerBi) return 'Informe o link do Power BI.';

    if (!linkPowerBi.startsWith(this.POWERBI_PREFIX)) {
      return `Link inválido. O link deve começar com: ${this.POWERBI_PREFIX}`;
    }

    return null;
  }

  // -------------------------
  // UX: nome repetido (client-side REAL)
  // -------------------------
  validarNomeUnico(): void {
    const nomeDigitado = this.normalizarComparacao(this.painel.nome);

    if (!nomeDigitado || this.nomesExistentes.length === 0) {
      this.isNomeRepetido = false;
      return;
    }

    this.isNomeRepetido = this.nomesExistentes.some((n) => {
      return this.normalizarComparacao(n) === nomeDigitado;
    });
  }

  // (opcional) evita bater no backend se o link já existe na lista
  private isLinkRepetido(link: string): boolean {
    // Se você quiser validar link duplicado antes, a Home também pode passar uma lista de links.
    // Como você só passou nomes, deixei off por padrão.
    return false;
  }

  // -------------------------
  // SAVE
  // -------------------------
  salvarPainel(): void {
    if (this.carregando) return;

    this.limparMensagens();

    const nome = this.normalizarCampo(this.painel.nome);
    const linkPowerBi = this.normalizarCampo(this.painel.linkPowerBi);

    // atualiza status de nome repetido (hint visual) antes de salvar
    this.validarNomeUnico();

    const erroValidacao = this.validarCampos(nome, linkPowerBi);
    if (erroValidacao) {
      this.setErro(erroValidacao);
      return;
    }

    // Se você quiser bloquear o save quando nome repetido:
    // if (this.isNomeRepetido) { this.setErro('Já existe um painel com esse nome.'); return; }

    if (this.isLinkRepetido(linkPowerBi)) {
      this.setErro('Você já possui este painel cadastrado (link duplicado).');
      return;
    }

    const headers = this.getAuthHeaders();
    if (!headers) {
      this.setErro('Sua sessão expirou. Faça login novamente.');
      return;
    }

    this.carregando = true;

    const payload = { nome, linkPowerBi };

    this.http.post<PainelDTO>(this.API_URL, payload, { headers }).subscribe({
      next: (criado) => {
        this.carregando = false;

        this.setSucesso('Painel cadastrado com sucesso! A capa será gerada automaticamente.');

        this.salvo.emit(criado);
        this.fecharModal();
      },
      error: (err) => {
        this.carregando = false;

        if (err?.status === 409) {
          this.setErro('Você já possui este painel cadastrado (link duplicado).');
          return;
        }

        if (err?.status === 400) {
          this.setErro(err?.error?.message || 'Dados inválidos.');
          return;
        }

        if (err?.status === 401 || err?.status === 403) {
          this.setErro('Sua sessão expirou. Faça login novamente.');
          return;
        }

        this.setErro('Falha ao salvar painel.');
      }
    });
  }
}
