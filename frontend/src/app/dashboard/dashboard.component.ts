import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {

  constructor(private router: Router) { }

  logout() {
    const raw = localStorage.getItem('user');
    let equipeKey: string | null = null;
    if (raw) {
      try {
        const user = JSON.parse(raw) as { id?: string | number };
        if (user?.id) equipeKey = `bicentral_selected_equipe:${user.id}`;
      } catch {}
    }

    localStorage.removeItem('user');
    localStorage.removeItem('token');
    if (equipeKey) localStorage.removeItem(equipeKey);
    localStorage.removeItem('bicentral_selected_equipe'); // limpeza da chave antiga não escopada
    this.router.navigate(['/login']);
  }
}
