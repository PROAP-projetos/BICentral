import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './auth.interceptor'; // Importa o "carimbo" que criamos

export const appConfig: ApplicationConfig = {
providers: [
provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Configura o cliente HTTP para usar o interceptor de autenticação
    provideHttpClient(
      withInterceptors([authInterceptor])
)
]
};
