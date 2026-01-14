import { Routes } from '@angular/router';

import { HomeComponent } from './home/home';
import { CadastroComponent } from './cadastro/cadastro.component';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { VerificacaoComponent } from './verificacao/verificacao.component';
import { AddPainelComponent } from './add-painel/add-painel.component';

import { authGuard } from './auth.guard';

export const routes: Routes = [
// Home: aqui aparece a vitrine de painéis do usuário
{ path: '', component: HomeComponent, canActivate: [authGuard] },

// Auth
{ path: 'login', component: LoginComponent },
{ path: 'cadastro', component: CadastroComponent },
{ path: 'verificar-email', component: VerificacaoComponent },

// Painéis (CRUD)
{ path: 'adicionar-painel', component: AddPainelComponent, canActivate: [authGuard] },
// se você reaproveitar AddPainelComponent pra edição via query param ?id=123,
// não precisa de uma rota extra.
// (se preferir rota por id, criamos depois: 'paineis/:id/editar')

// Outras páginas protegidas
{ path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },

// Fallback
{ path: '**', redirectTo: '' }
];
