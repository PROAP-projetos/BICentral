import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { CadastroComponent } from './cadastro/cadastro.component';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { authGuard } from './auth.guard';
import { VerificacaoComponent } from './verificacao/verificacao.component';
import { AddPainelComponent } from './add-painel/add-painel.component';

//advinha pô, eu acho que as rotas mexem com as rotas, mas posso estar errado né

export const routes: Routes = [
{ path: '', component: HomeComponent },
{ path: 'cadastro', component: CadastroComponent },
{ path: 'login', component: LoginComponent },
{ path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
{ path: 'verificar-email', component: VerificacaoComponent },
{ path: 'adicionar-painel', component: AddPainelComponent },
];
