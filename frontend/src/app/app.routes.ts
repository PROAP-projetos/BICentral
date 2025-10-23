import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { CadastroComponent } from './cadastro/cadastro.component';
import { AddPainelComponent } from './add-painel/add-painel.component';

export const routes: Routes = [
    { path: '', component: HomeComponent },
    { path: 'cadastro', component: CadastroComponent },

    { path: 'adicionar-painel', component: AddPainelComponent}
];
