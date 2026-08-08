import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

type AlertType = 'error' | 'success' | 'info';

@Component({
  selector: 'app-suporte',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './suporte.component.html',
  styleUrls: ['./suporte.component.css']
})
export class SuporteComponent {
  readonly faqs = [
    {
      pergunta: 'Como recupero meu acesso?',
      resposta: 'Use a tela de login com seu e-mail já cadastrado. Se a conta ainda não estiver ativada, verifique o e-mail de confirmação.'
    },
    {
      pergunta: 'Não consigo visualizar meu painel, o que fazer?',
      resposta: 'Confirme se o link do Power BI está correto e se você tem permissão de visualização no workspace de origem.'
    },
    {
      pergunta: 'Como editar ou remover um painel?',
      resposta: 'Na Home, abra o painel e use as ações de edição/exclusão disponíveis para seu perfil.'
    },
    {
      pergunta: 'Com quem falo para dúvidas gerais?',
      resposta: 'Você pode enviar sua dúvida neste formulário. A equipe do BI Central responderá pelo e-mail informado.'
    }
  ];

  openFaqIndex: number | null = 0;
  sending = false;
  feedbackMessage: string | null = null;
  feedbackType: AlertType = 'info';
  readonly form;

  constructor(private readonly fb: FormBuilder, private readonly http: HttpClient) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(120)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
      assunto: ['', [Validators.required, Validators.maxLength(160)]],
      mensagem: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(4000)]]
    });
  }

  toggleFaq(index: number): void {
    this.openFaqIndex = this.openFaqIndex === index ? null : index;
  }

  enviar(): void {
    this.feedbackMessage = null;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.feedbackType = 'error';
      this.feedbackMessage = 'Preencha os campos obrigatórios antes de enviar.';
      return;
    }

    this.sending = true;

    this.http.post<{ mensagem?: string }>('/api/suporte', this.form.getRawValue()).subscribe({
      next: (res) => {
        this.sending = false;
        this.feedbackType = 'success';
        this.feedbackMessage = res?.mensagem || 'Mensagem enviada com sucesso para o suporte.';
        this.form.reset();
      },
      error: (err) => {
        this.sending = false;
        this.feedbackType = 'error';
        this.feedbackMessage =
          err?.error?.mensagem ||
          err?.error?.message ||
          'Não foi possível enviar sua mensagem no momento. Tente novamente.';
      }
    });
  }

  campoInvalido(nomeCampo: 'nome' | 'email' | 'assunto' | 'mensagem'): boolean {
    const field = this.form.get(nomeCampo);
    return !!field && field.invalid && (field.touched || field.dirty);
  }
}
