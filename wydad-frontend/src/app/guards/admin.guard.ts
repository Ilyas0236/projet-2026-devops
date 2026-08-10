import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.currentUserValue) {
    const role = localStorage.getItem('wydad_role');
    if (role === 'ADMIN') {
      return true;
    }
  }

  router.navigate(['/']);
  return false;
};
