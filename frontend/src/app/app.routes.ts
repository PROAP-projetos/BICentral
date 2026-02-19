import { Routes } from '@angular/router';

import { HomeComponent } from './home/home';
import { CadastroComponent } from './cadastro/cadastro.component';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { VerificacaoComponent } from './verificacao/verificacao.component';
import { AddPainelComponent } from './add-painel/add-painel.component';
import { EquipeComponent } from './equipe/equipe.component';

import { authGuard } from './auth.guard';

export const routes: Routes = [
  // Home
  { path: '', component: HomeComponent, canActivate: [authGuard] },

  // Auth
  { path: 'login', component: LoginComponent },
  { path: 'cadastro', component: CadastroComponent },
  { path: 'verificar-email', component: VerificacaoComponent },

  // 2. ADICIONE ESTA LINHA AQUI:
  { path: 'equipe', component: EquipeComponent, canActivate: [authGuard] },

  // Painéis (CRUD)
  { path: 'adicionar-painel', component: AddPainelComponent, canActivate: [authGuard] },

  // Outras páginas protegidas
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },

  // Fallback
  { path: '**', redirectTo: '' }
];
