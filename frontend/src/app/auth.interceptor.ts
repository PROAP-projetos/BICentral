import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  // 1. Corrigido para localStorage (L e S maiúsculos)
  const token = localStorage.getItem('token');
  const userJson = localStorage.getItem('user');

  // 2. Ignora rotas públicas para não enviar token vazio ou antigo
  if (req.url.includes('/api/usuarios/login')) {
    return next(req);
  }

  let finalToken = token;

  // 3. Corrigido para JSON.parse (JSON em maiúsculas)
  if (!finalToken && userJson) {
    try {
      const user = JSON.parse(userJson);
      finalToken = user.token;
    } catch (e) {
      console.error('Erro ao processar JSON do usuário no interceptor', e);
    }
  }

  const finalReq = finalToken
    ? req.clone({
        // 5. Corrigido para setHeaders (H maiúsculo)
        setHeaders: {
          Authorization: `Bearer ${finalToken}`
        }
      })
    : req;

  return next(finalReq).pipe(
    catchError((err) => {
      if (err.status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        router.navigate(['/login']);
      }
      return throwError(() => err);
    })
  );
};
