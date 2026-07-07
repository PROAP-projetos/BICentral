import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AdminService } from './services/admin.service';

export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  const adminService = inject(AdminService);

  return adminService.souAdmin().pipe(
    map((resposta) => {
      if (resposta.admin) {
        return true;
      }
      router.navigate(['/']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/']);
      return of(false);
    })
  );
};
