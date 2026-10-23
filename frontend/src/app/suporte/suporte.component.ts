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
      pergunta: 'Esqueci minha senha, e agora?',
      resposta: 'Na tela de login, clique em "Esqueci minha senha" e informe seu e-mail. Você recebe um link (válido por 1 hora) pra escolher uma senha nova. Se não achar o e-mail, confira a caixa de spam.'
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
      pergunta: 'O que é o proIAp?',
      resposta: 'É o agente de IA do BICentral. Ele lê os dados reais da PROAP (PAT do ano corrente, tarefas por unidade) e responde suas perguntas na hora, em texto ou em gráfico — é só clicar em "Pergunte ao agente".'
    },
    {
      pergunta: 'O que eu posso perguntar pro proIAp?',
      resposta: 'Rankings de execução por unidade, detalhamento de uma unidade ou ação específica, suas próprias tarefas e prazos, contagem de ações por departamento, e pedidos de relatório completo de uma unidade (fica pronto em até 30s e aparece no ícone de documento no topo da tela).'
    },
    {
      pergunta: 'Posso parar uma resposta do proIAp no meio?',
      resposta: 'Sim — enquanto ele está respondendo, o botão de enviar vira um botão de parar. Clicar nele cancela a geração em andamento.'
    },
    {
      pergunta: 'Por que meu acesso ao proIAp tem um limite de uso?',
      resposta: 'Durante o período de teste, cada tester tem um orçamento individual de uso — a barra abaixo do campo de pergunta mostra quanto já foi usado. É só pra controlar custo no período de teste, não afeta o resto do BICentral.'
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
