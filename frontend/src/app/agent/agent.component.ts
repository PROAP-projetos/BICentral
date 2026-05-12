import { CommonModule } from '@angular/common';
import { AfterViewChecked, AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { finalize } from 'rxjs';
import { AgentService } from '../services/agent.service';

interface FonteDisponivel {
  nome: string;
  acesso: 'publico' | 'privado';
  tipo: 'planilha' | 'pdf' | 'relatorio' | 'documento';
}

interface EquipeSelecionada {
  id: number;
  nome: string;
  role?: string;
}

@Component({
  selector: 'app-agent',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './agent.html',
  styleUrls: ['./agent.css']
})
export class AgentComponent implements AfterViewInit, AfterViewChecked {
  private static readonly SELECTED_EQUIPE_KEY = 'bicentral_selected_equipe';

  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;
  private scrollPendente = true;

  usuarioLogado = 'dallyla.moraes';
  equipeSelecionada = 'COPLAN';
  equipeId?: number;

  fontesDisponiveis: FonteDisponivel[] = [];

  modeloAtivo = {
    nome: 'Gemini 2.5 Flash',
    detalhes: 'via API · gemini-embedding-001'
  };

  messages: { from: 'bot' | 'user'; text: string }[] = [
    { from: 'bot', text: 'Olá, Dallyla! Sou o agente de IA do BICentral, alimentado pelos documentos da PROAP.\n\nPosso consultar planilhas do PAT, o PDI, indicadores do TCU e outros documentos ingeridos. Acesso apenas o que sua equipe tem permissão de visualizar.\n\nComo posso ajudar?' }
  ];

  suggestions = [
    'Quantas tarefas do PAT estão em atraso?',
    'Qual é a meta 3.2 do PDI?',
    'Resumo dos indicadores do TCU'
  ];

  input = '';
  carregando = false;
  carregandoFontes = false;
  erro = '';

  constructor(private agentService: AgentService) {
    const userRaw = localStorage.getItem('user');
    if (userRaw) {
      try {
        const user = JSON.parse(userRaw);
        this.usuarioLogado = user.username || user.email || this.usuarioLogado;
      } catch {
        this.usuarioLogado = 'dallyla.moraes';
      }
    }

    this.carregarEquipeSelecionada();
    this.carregarFontes();
  }

  ngAfterViewInit(): void {
    this.agendarScrollParaFim();
  }

  ngAfterViewChecked(): void {
    if (!this.scrollPendente) return;

    this.scrollPendente = false;
    this.scrollMessagesToBottom();
  }

  send() {
    const text = (this.input || '').trim();
    if (!text || this.carregando) return;

    if (!this.equipeId) {
      this.erro = 'Selecione uma equipe antes de consultar o agente.';
      return;
    }

    this.messages.push({ from: 'user', text });
    this.input = '';
    this.erro = '';
    this.carregando = true;
    this.agendarScrollParaFim();

    this.agentService.consultar(text, this.equipeId)
      .pipe(finalize(() => this.carregando = false))
      .subscribe({
        next: (resposta) => {
          this.messages.push({ from: 'bot', text: resposta.resposta });
          this.agendarScrollParaFim();
        },
        error: (err) => {
          const mensagem = err?.error?.mensagem || 'Não foi possível consultar o agente agora.';
          this.erro = mensagem;
          this.messages.push({ from: 'bot', text: mensagem });
          this.agendarScrollParaFim();
        }
      });
  }

  useSuggestion(s: string) {
    this.input = s;
    this.send();
  }

  private carregarEquipeSelecionada(): void {
    const raw = localStorage.getItem(AgentComponent.SELECTED_EQUIPE_KEY);
    if (!raw) return;

    try {
      const equipe = JSON.parse(raw) as Partial<EquipeSelecionada>;
      if (!equipe.id || !equipe.nome) return;

      this.equipeId = equipe.id;
      this.equipeSelecionada = equipe.nome;
    } catch {
      this.equipeId = undefined;
    }
  }

  private carregarFontes(): void {
    if (!this.equipeId) return;

    this.carregandoFontes = true;
    this.agentService.listarFontes(this.equipeId)
      .pipe(finalize(() => this.carregandoFontes = false))
      .subscribe({
        next: (response) => {
          this.equipeSelecionada = response.equipe || this.equipeSelecionada;
          this.modeloAtivo = response.modelo || this.modeloAtivo;
          this.fontesDisponiveis = (response.fontes || []).map((fonte) => ({
            ...fonte,
            tipo: this.getTipoFonte(fonte.nome)
          }));
        },
        error: () => {
          this.fontesDisponiveis = [];
        }
      });
  }

  private getTipoFonte(nome: string): FonteDisponivel['tipo'] {
    const extensao = nome.split('.').pop()?.toLowerCase();
    if (extensao === 'xlsx') return 'planilha';
    if (extensao === 'pdf') return 'pdf';
    if (nome.toLowerCase().includes('relatorio')) return 'relatorio';
    return 'documento';
  }

  private agendarScrollParaFim(): void {
    this.scrollPendente = true;
  }

  private scrollMessagesToBottom(): void {
    window.requestAnimationFrame(() => {
      const container = this.messagesContainer?.nativeElement;
      if (!container) return;
      container.scrollTop = container.scrollHeight;
    });
  }
}
