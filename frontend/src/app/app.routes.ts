import { Routes } from '@angular/router';

import { AceitarConviteComponent } from './aceitar-convite/aceitar-convite.component';
import { AddPainelComponent } from './add-painel/add-painel.component';
import { AgentComponent } from './agent/agent.component';
import { CadastroComponent } from './cadastro/cadastro.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { EquipeComponent } from './equipe/equipe.component';
import { HomeComponent } from './home/home';
import { IngestaoIaComponent } from './ingestao-ia/ingestao-ia';
import { LoginComponent } from './login/login.component';
import { PainelAdminComponent } from './painel-admin/painel-admin.component';
import { SuporteComponent } from './suporte/suporte.component';
import { VerificacaoComponent } from './verificacao/verificacao.component';

import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  // Home
  { path: '', component: HomeComponent, canActivate: [authGuard] },

  // Auth
  { path: 'login', component: LoginComponent },
  { path: 'cadastro', component: CadastroComponent },
  { path: 'verificar-email', component: VerificacaoComponent },
  { path: 'aceitar-convite', component: AceitarConviteComponent },

  // Equipes
  { path: 'equipe', component: EquipeComponent, canActivate: [authGuard] },
  { path: 'suporte', component: SuporteComponent, canActivate: [authGuard] },

  // Painéis (CRUD)
  { path: 'adicionar-painel', component: AddPainelComponent, canActivate: [authGuard] },

  { path: 'ingestao-ia', component: IngestaoIaComponent, canActivate: [authGuard] },

  // Agente de IA
  { path: 'agente', component: AgentComponent, canActivate: [authGuard] },

  // Outras páginas protegidas
  { path: 'admin', component: PainelAdminComponent, canActivate: [authGuard, adminGuard] },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },

  // Fallback
  { path: '**', redirectTo: '' }

];
