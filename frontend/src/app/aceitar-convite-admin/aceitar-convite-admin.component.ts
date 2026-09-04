import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AdminService } from '../services/admin.service';

@Component({
  selector: 'app-aceitar-convite-admin',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './aceitar-convite-admin.component.html',
  styleUrls: ['./aceitar-convite-admin.component.css']
})
export class AceitarConviteAdminComponent implements OnInit {
  loading = true;
  success = false;
  title = 'Validando convite';
  message = 'Estamos conferindo seu link de convite.';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adminService: AdminService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.loading = false;
      this.success = false;
      this.title = 'Convite inválido';
      this.message = 'O link informado não possui um token de convite válido.';
      return;
    }

    this.adminService.aceitarConviteAdmin(token).subscribe({
      next: (resposta) => {
        this.loading = false;
        this.success = true;
        this.title = 'Convite aceito';
        this.message = resposta.mensagem;
        setTimeout(() => {
          if (localStorage.getItem('user')) {
            this.router.navigate(['/']);
            return;
          }
          this.router.navigate(['/login']);
        }, 4000);
      },
      error: (erro) => {
        this.loading = false;
        this.success = false;
        this.title = this.resolveTitle(erro?.status);
        this.message = erro?.error?.mensagem || 'Não foi possível processar o convite.';
      }
    });
  }

  private resolveTitle(status: number | undefined): string {
    if (status === 410) {
      return 'Convite expirado';
    }
    if (status === 409) {
      return 'Convite já utilizado';
    }
    if (status === 404) {
      return 'Convite indisponível';
    }
    return 'Erro ao aceitar convite';
  }
}
