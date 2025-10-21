// src/app/home/home.component.ts

import { Component } from '@angular/core';
@Component({
selector: 'app-home',
templateUrl: './home.html',
styleUrls: ['./home.css']
})
export class HomeComponent {
// Variável que controla se o menu está aberto ou fechado
isMenuOpen: boolean = false;

// Função para alternar o estado do menu
toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }
}
