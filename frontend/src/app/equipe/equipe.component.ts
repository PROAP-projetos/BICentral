import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EquipeService, Equipe } from '../services/equipe.services';

export type UserRole = 'viewer' | 'editor' | 'admin';

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './equipe.component.html',
  styleUrls: ['./equipe.component.css']
})
export class EquipeComponent implements OnInit {
  // 2. Trocamos 'members' por 'equipes'
  equipes: Equipe[] = [];
  currentUserRole: UserRole = 'viewer';

  // 3. Mantemos a lógica do seu Modal!
  isConfirmOpen = false;
  equipeToRemove: Equipe | null = null;

  // 4. O formulário agora reflete o Backend (Nome e Descrição)
  novaEquipe: Equipe = {
    nome: '',
    descricao: ''
  };

  constructor(private equipeService: EquipeService) {}

  ngOnInit(): void {
    this.currentUserRole = this.getRoleFromStorage();
    // 5. Busca as equipes no Banco de Dados assim que a tela abre
    this.carregarEquipes();
  }

  // Mantido intacto!
  get isAdmin(): boolean {
    return this.currentUserRole === 'admin';
  }

  carregarEquipes(): void {
    this.equipeService.listarMinhasEquipes().subscribe({
      next: (dados) => this.equipes = dados,
      error: (erro) => console.error('Erro ao buscar equipes', erro)
    });
  }

  // 6. Criar agora vai pro Java (Restrito para Admin)
  criarEquipe(): void {
    if (!this.isAdmin) return;

    const nome = this.novaEquipe.nome.trim();
    if (!nome) return;

    this.equipeService.criar(this.novaEquipe).subscribe({
      next: (equipeCriada) => {
        this.equipes.push(equipeCriada);
        this.novaEquipe = { nome: '', descricao: '' }; // Limpa o form
      },
      error: (erro) => console.error('Erro ao criar equipe', erro)
    });
  }

  // 7. Toda a sua lógica de Modal mantida e adaptada para Equipes
  removerEquipe(equipe: Equipe): void {
    if (!this.isAdmin) return;
    this.equipeToRemove = equipe;
    this.isConfirmOpen = true;
  }

  confirmarRemocao(): void {
    if (!this.isAdmin || !this.equipeToRemove) return;

    // ATENÇÃO: Como o Java ainda não tem Rota DELETE, vamos remover
    // apenas visualmente da tela por enquanto para o Modal funcionar.
    const id = this.equipeToRemove.id;
    this.equipes = this.equipes.filter(e => e.id !== id);
    console.warn('Backend precisa da rota DELETE para remover do banco real!');

    this.fecharConfirmacao();
  }

  fecharConfirmacao(): void {
    this.isConfirmOpen = false;
    this.equipeToRemove = null;
  }

  onConfirmOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharConfirmacao();
    }
  }

  // Mantido intacto!
  private getRoleFromStorage(): UserRole {
    const userStr = localStorage.getItem('user');
    let role: string | null = null;

    if (userStr) {
      try {
        role = JSON.parse(userStr)?.role ?? null;
      } catch {
        role = null;
      }
    }

    role = role || localStorage.getItem('role') || 'viewer';

    if (role === 'viewer' || role === 'editor' || role === 'admin') {
      return role;
    }
    return 'viewer';
  }
}
