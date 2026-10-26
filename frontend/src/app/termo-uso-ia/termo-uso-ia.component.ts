import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// Mudou o texto? Suba TermoUsoService.VERSAO_ATUAL no backend.
@Component({
  selector: 'app-termo-uso-ia',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './termo-uso-ia.component.html',
  styleUrl: './termo-uso-ia.component.css'
})
export class TermoUsoIaComponent {
  @Input() exigirAceite = false;
  @Input() versao = '';
  @Input() salvando = false;
  @Input() erro = '';
  @Output() aceito = new EventEmitter<void>();
  @Output() recusado = new EventEmitter<void>();
  @Output() fechado = new EventEmitter<void>();

  readonly vigenteDesde = '23 de outubro de 2026';
  declarouCiencia = false;

  fecharPeloFundo() {
    if (!this.exigirAceite) {
      this.fechado.emit();
    }
  }
}
