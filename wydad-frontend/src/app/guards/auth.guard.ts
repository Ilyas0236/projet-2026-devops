import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.currentUserValue && authService.isTokenValid()) {
    return true;
  }

  if (authService.currentUserValue && !authService.isTokenValid()) {
    authService.logout();
  }
  // Quality-final — propage l'URL d'origine via returnUrl pour que LoginComponent
  // y ramène l'utilisateur après authentification. Aligné sur role.guard.ts:15.
  router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
  return false;
};
