import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard de rôle générique : roleGuard('JOUEUR') n'autorise que le rôle JOUEUR.
 * Le contrôle serveur reste l'autorité (le guard est une commodité UX, pas la sécurité).
 */
export const roleGuard = (...allowedRoles: string[]): CanActivateFn => (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isTokenValid()) {
    authService.logout();
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  if (allowedRoles.includes(authService.getTokenRole() ?? '')) {
    return true;
  }

  router.navigate(['/']);
  return false;
};

/** Espace joueur : réservé au rôle JOUEUR (valeur backend, enum Role.java). */
export const joueurGuard: CanActivateFn = roleGuard('JOUEUR');

/**
 * Espace staff : STAFF, ENTRAINEUR et PRESIDENT (Phase 5 — le président et
 * l'entraîneur programment des appels depuis ce dashboard ; le serveur
 * revalide chaque action par rôle).
 */
export const staffGuard: CanActivateFn = roleGuard('STAFF', 'ENTRAINEUR', 'PRESIDENT');

/** Suivi académie : réservé au rôle PARENT. */
export const parentGuard: CanActivateFn = roleGuard('PARENT');
