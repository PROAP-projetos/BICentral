import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const userJson = localStorage.getItem('user');
  
  if (userJson) {
    try {
      const user = JSON.parse(userJson);
      const token = user.token;

      if (token) {
        const authReq = req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        });
        return next(authReq);
      }
    } catch (e) {
      console.error('Erro no interceptor JWT:', e);
    }
  }

  return next(req);
};