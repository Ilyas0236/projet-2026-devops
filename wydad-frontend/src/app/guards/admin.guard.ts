import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isTokenValid() && authService.getTokenRole() === 'ADMIN') {
    return true;
  }

  // Non-admin ou token expire : nettoyage et retour a l'accueil
  if (!authService.isTokenValid()) {
    authService.logout();
  }
  router.navigate(['/']);
  return false;
};
