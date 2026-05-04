import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // 1. Corrigido para localStorage (L e S maiúsculos)
  const token = localStorage.getItem('token');
  const userJson = localStorage.getItem('user');

  // 2. Ignora rotas públicas para não enviar token vazio ou antigo
  if (req.url.includes('/api/usuarios/login') || req.url.includes('/api/convites/aceitar')) {
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

  // 4. Se temos um token, clonamos a requisição
  if (finalToken) {
    const authReq = req.clone({
      // 5. Corrigido para setHeaders (H maiúsculo)
      setHeaders: {
        Authorization: `Bearer ${finalToken}`
      }
    });
    return next(authReq);
  }

  return next(req);
};
