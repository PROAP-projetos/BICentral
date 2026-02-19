import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MOCK_MEMBERS, TeamMember, UserRole } from '../mocks/equipe.mock';

interface NewMemberForm {
  name: string;
  email: string;
  role: UserRole;
}

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './equipe.component.html',
  styleUrls: ['./equipe.component.css']
})
export class EquipeComponent implements OnInit {
  members: TeamMember[] = [];
  currentUserRole: UserRole = 'viewer';
  roleOptions: UserRole[] = ['viewer', 'editor', 'admin'];
  isConfirmOpen = false;
  memberToRemove: TeamMember | null = null;

  form: NewMemberForm = {
    name: '',
    email: '',
    role: 'viewer'
  };

  ngOnInit(): void {
    this.members = [...MOCK_MEMBERS];
    this.currentUserRole = this.getRoleFromStorage();
  }

  get isAdmin(): boolean {
    return this.currentUserRole === 'admin';
  }

  addMember(): void {
    if (!this.isAdmin) return;

    const name = this.form.name.trim();
    const email = this.form.email.trim();

    if (!name || !email) return;

    const nextId = this.members.length
      ? Math.max(...this.members.map(m => m.id)) + 1
      : 1;

    this.members = [
      ...this.members,
      { id: nextId, name, email, role: this.form.role }
    ];

    this.form = { name: '', email: '', role: 'viewer' };
  }

  removeMember(member: TeamMember): void {
    if (!this.isAdmin) return;
    this.memberToRemove = member;
    this.isConfirmOpen = true;
  }

  updateRole(member: TeamMember, role: UserRole): void {
    if (!this.isAdmin) return;
    this.members = this.members.map(m =>
      m.id === member.id ? { ...m, role } : m
    );
  }

  confirmarRemocao(): void {
    if (!this.isAdmin || !this.memberToRemove) return;
    const id = this.memberToRemove.id;
    this.members = this.members.filter(m => m.id !== id);
    this.fecharConfirmacao();
  }

  fecharConfirmacao(): void {
    this.isConfirmOpen = false;
    this.memberToRemove = null;
  }

  onConfirmOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.fecharConfirmacao();
    }
  }

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
