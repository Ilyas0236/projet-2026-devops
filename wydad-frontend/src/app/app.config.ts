import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { AuthService } from './services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Session stable (F5 / retour arrière) : au démarrage, si un access
    // token existe mais est expiré (ou illisible), on tente une
    // reconnexion silencieuse AVANT le premier rendu — les guards voient
    // ainsi un token frais au lieu de déconnecter.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      const hasToken = !!localStorage.getItem('wydad_token');
      const expired = !auth.isTokenValid();
      if (!hasToken || !expired) {
        return Promise.resolve();
      }
      if (!localStorage.getItem('wydad_refresh_token')) {
        // Token périmé sans filet de rattrapage : purge pour éviter tout
        // état fantôme.
        auth.logout();
        return Promise.resolve();
      }
      return firstValueFrom(auth.refreshSession())
        .then(() => void 0)
        .catch(() => {
          // Refresh refusé → purge propre ; l'utilisateur se reconnecte.
          auth.logout();
        });
    })
  ]
};
