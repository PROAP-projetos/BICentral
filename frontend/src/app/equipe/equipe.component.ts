import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EquipeService, Equipe, MembroEquipe } from '../services/equipe.service';

export type UserRole = 'VIEWER' | 'EDITOR' | 'ADMIN';

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './equipe.component.html',
  styleUrls: ['./equipe.component.css']
})
export class EquipeComponent implements OnInit {
  private static readonly SELECTED_EQUIPE_KEY = 'bicentral_selected_equipe';

  equipes: Equipe[] = [];

  isConfirmOpen = false;
  equipeToRemove: Equipe | null = null;
  isMemberConfirmOpen = false;
  memberToRemove: MembroEquipe | null = null;

  novaEquipe: Equipe = {
    nome: '',
    descricao: ''
  };

  isEditando = false;
  idEquipeSendoEditada: number | null = null;

  // Gestão de Membros
  membros: MembroEquipe[] = [];
  equipeAtiva: Equipe | null = null;
  conviteEmail = '';
  conviteRole: UserRole = 'VIEWER';
  
  mensagemFeedback = '';
  tipoFeedback: 'sucesso' | 'erro' = 'sucesso';
  carregandoMembros = false;

  constructor(private equipeService: EquipeService) {}

  ngOnInit(): void {
    this.carregarEquipes();
  }

  podeEditar(equipe: Equipe): boolean {
    return equipe.role === 'ADMIN' || equipe.role === 'EDITOR';
  }

  podeRemover(equipe: Equipe): boolean {
    return equipe.role === 'ADMIN';
  }

  podeGerenciarMembros(equipe: Equipe | null): boolean {
    return equipe?.role === 'ADMIN';
  }

  carregarEquipes(): void {
    this.equipeService.listarMinhasEquipes().subscribe({
      next: (dados) => {
        this.equipes = dados;
        this.restaurarEquipeSelecionada();
      },
      error: (erro) => console.error('Erro ao buscar equipes', erro)
    });
  }

  selecionarEquipe(equipe: Equipe): void {
    this.equipeAtiva = equipe;
    this.salvarEquipeSelecionada(equipe);
    this.carregarMembros(equipe.id!);
    this.limparFeedback();
  }

  private restaurarEquipeSelecionada(): void {
    const raw = localStorage.getItem(EquipeComponent.SELECTED_EQUIPE_KEY);
    if (!raw) return;

    try {
      const saved = JSON.parse(raw) as { id?: number };
      if (!saved?.id) return;

      const match = this.equipes.find((eq) => eq.id === saved.id);
      if (match?.id) {
        this.equipeAtiva = match;
        this.salvarEquipeSelecionada(match);
        this.carregarMembros(match.id);
      }
    } catch {
      localStorage.removeItem(EquipeComponent.SELECTED_EQUIPE_KEY);
    }
  }

  private salvarEquipeSelecionada(equipe: Equipe): void {
    if (!equipe.id) return;

    localStorage.setItem(
      EquipeComponent.SELECTED_EQUIPE_KEY,
      JSON.stringify({
        id: equipe.id,
        nome: equipe.nome,
        role: (equipe.role || 'VIEWER').toUpperCase()
      })
    );
  }

  carregarMembros(equipeId: number): void {
    this.carregandoMembros = true;
    this.equipeService.listarMembros(equipeId).subscribe({
      next: (dados) => {
        this.membros = dados;
        this.carregandoMembros = false;
      },
      error: (erro) => {
        this.exibirFeedback('Erro ao carregar membros.', 'erro');
        this.carregandoMembros = false;
      }
    });
  }

  enviarConvite(): void {
    if (!this.equipeAtiva || !this.conviteEmail) return;

    this.equipeService.enviarConvite(this.equipeAtiva.id!, this.conviteEmail, this.conviteRole).subscribe({
      next: () => {
        this.exibirFeedback('Convite enviado com sucesso.', 'sucesso');
        this.conviteEmail = '';
        this.conviteRole = 'VIEWER';
      },
      error: (erro) => {
        const msg = erro.error?.mensagem || 'Erro ao enviar convite. Verifique o e-mail e as permissões.';
        this.exibirFeedback(msg, 'erro');
      }
    });
  }

  removerMembro(membro: MembroEquipe): void {
    if (!this.equipeAtiva) return;
    this.memberToRemove = membro;
    this.isMemberConfirmOpen = true;
  }

  confirmarRemocaoMembro(): void {
    if (!this.equipeAtiva || !this.memberToRemove) return;

    this.equipeService.removerMembro(this.equipeAtiva.id!, this.memberToRemove.usuarioId).subscribe({
      next: () => {
        this.exibirFeedback('Membro removido com sucesso!', 'sucesso');
        this.carregarMembros(this.equipeAtiva!.id!);
        this.fecharConfirmacaoMembro();
      },
      error: (erro) => {
        const msg = erro.error?.mensagem || 'Erro ao remover membro.';
        this.exibirFeedback(msg, 'erro');
        this.fecharConfirmacaoMembro();
      }
    });
  }

  alterarPapel(membro: MembroEquipe, novoRole: string): void {
    if (!this.equipeAtiva) return;

    this.equipeService.alterarPapel(this.equipeAtiva.id!, membro.usuarioId, novoRole).subscribe({
      next: () => {
        this.exibirFeedback('Papel alterado com sucesso!', 'sucesso');
        this.carregarMembros(this.equipeAtiva!.id!);
      },
      error: (erro) => {
        const msg = erro.error?.mensagem || 'Erro ao alterar papel.';
        this.exibirFeedback(msg, 'erro');
        // Reverte localmente para o valor anterior se necessário (opcional)
      }
    });
  }

  exibirFeedback(msg: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagemFeedback = msg;
    this.tipoFeedback = tipo;
    setTimeout(() => this.limparFeedback(), 5000);
  }

  limparFeedback(): void {
    this.mensagemFeedback = '';
  }

  criarEquipe(): void {
    const nome = this.novaEquipe.nome.trim();
    if (!nome) return;

    this.equipeService.criar(this.novaEquipe).subscribe({
      next: (equipeCriada) => {
        this.equipes.push(equipeCriada);
        this.novaEquipe = { nome: '', descricao: '' }; // Limpa o form
        this.exibirFeedback('Equipe criada com sucesso!', 'sucesso');
        if (!this.equipeAtiva) {
          this.selecionarEquipe(equipeCriada);
        }
      },
      error: (erro) => {
        this.exibirFeedback('Erro ao criar equipe.', 'erro');
      }
    });
  }
  salvar(): void {
    if (this.isEditando && this.idEquipeSendoEditada) {
      this.equipeService.atualizar(this.idEquipeSendoEditada, this.novaEquipe).subscribe({
        next: () => {
          this.carregarEquipes(); // Recarrega a lista
          this.cancelarEdicao();
          this.exibirFeedback('Equipe atualizada!', 'sucesso');
        },
        error: () => this.exibirFeedback('Erro ao atualizar equipe.', 'erro')
      });
    } else {
      this.criarEquipe(); // Sua função original de POST
    }
  }

  prepararEdicao(equipe: Equipe){
    this.isEditando = true;
    this.idEquipeSendoEditada = equipe.id!;
    this.novaEquipe = {...equipe};
  }

  cancelarEdicao(){
    this.isEditando = false;
    this.idEquipeSendoEditada = null;
    this.novaEquipe = {nome: '', descricao: ''};
  }


  removerEquipe(equipe: Equipe): void {
    this.equipeToRemove = equipe;
    this.isConfirmOpen = true;
  }

  confirmarRemocao(): void {
    if (!this.equipeToRemove) return;

    const id = this.equipeToRemove.id;
    if (id === undefined) return;

    this.equipeService.remover(id).subscribe({
      next: () =>{
        this.equipes = this.equipes.filter(e => e.id !== id);
        if (this.equipeAtiva?.id === id) {
          this.equipeAtiva = null;
          this.membros = [];
          localStorage.removeItem(EquipeComponent.SELECTED_EQUIPE_KEY);
        }
        this.exibirFeedback('Equipe removida com sucesso.', 'sucesso');
        this.fecharConfirmacao();
      },
      error: (erro) =>{
        const msg = erro.error?.mensagem || 'Erro ao remover equipe. Verifique suas permissões.';
        this.exibirFeedback(msg, 'erro');
        this.fecharConfirmacao();
      }
    });
  }

  fecharConfirmacao(): void {
    this.isConfirmOpen = false;
    this.equipeToRemove = null;
  }

  fecharConfirmacaoMembro(): void {
    this.isMemberConfirmOpen = false;
    this.memberToRemove = null;
  }

  onConfirmOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharConfirmacao();
    }
  }

  onMemberConfirmOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharConfirmacaoMembro();
    }
  }
}
