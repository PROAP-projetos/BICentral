import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { CadastroComponent } from './cadastro/cadastro.component';

export const routes: Routes = [
    { path: '', component: HomeComponent },
    { path: 'cadastro', component: CadastroComponent }
];
