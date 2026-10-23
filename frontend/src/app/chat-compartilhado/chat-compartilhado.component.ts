import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AgentService, MensagemHistorico } from '../services/agent.service';
import { GraficoIaComponent } from '../grafico-ia/grafico-ia';

// Página pública (sem login, ver app.routes.ts e o "/api/proiap/compartilhado/**" liberado no
// SecurityConfig do backend) — quem recebe o link de "Compartilhar" numa conversa do agente cai
// aqui, só leitura.
@Component({
  selector: 'app-chat-compartilhado',
  standalone: true,
  imports: [CommonModule, GraficoIaComponent],
  templateUrl: './chat-compartilhado.component.html',
  styleUrls: ['./chat-compartilhado.component.css']
})
export class ChatCompartilhadoComponent implements OnInit {
  titulo = '';
  mensagens: MensagemHistorico[] = [];
  carregando = true;
  erro = false;

  constructor(private route: ActivatedRoute, private agentService: AgentService) {}

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.erro = true;
      this.carregando = false;
      return;
    }

    this.agentService.buscarSessaoCompartilhada(token).subscribe({
      next: (sessao) => {
        this.titulo = sessao.titulo;
        this.mensagens = sessao.mensagens;
        this.carregando = false;
      },
      error: () => {
        this.erro = true;
        this.carregando = false;
      }
    });
  }
}
