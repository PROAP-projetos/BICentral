import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector : 'app-adm-painel',
  standalone : true,
  imports : [  ],
  templateUrl : './adm-painel.component.html',
  styleUrls : [ './adm-painel.component.css' ]
})
export class AdmPainelComponent {

  constructor(private router: Router) { }

  logout() {
    localStorage.removeItem('user');
    this.router.navigate(['/login']);
  }
}
