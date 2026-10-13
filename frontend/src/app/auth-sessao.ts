// Formato que POST /api/usuarios/login devolve (ver UsuarioController.login).
export interface RespostaLogin {
  token: string;
  id: string;
  username: string;
  tester?: boolean;
  convidado?: boolean;
}

// Grava a sessão no formato que o interceptor e a Home leem, e devolve a rota de destino:
// tester e convidado caem direto no agente em vez da Home.
export function salvarSessaoLogin(response: RespostaLogin): string {
  localStorage.removeItem('token');
  localStorage.removeItem('user');

  localStorage.setItem('token', response.token);
  localStorage.setItem('user', JSON.stringify({
    id: response.id,
    username: response.username,
    token: response.token
  }));

  return (response.tester || response.convidado) ? '/agente' : '/';
}
