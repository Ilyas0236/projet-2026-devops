import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/** Décode le payload JWT (sans vérification de signature — c'est le rôle du serveur). */
function decodeJwtPayload(token: string): { exp?: number; role?: string } | null {
  try {
        const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(b64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('wydad_token');
  if (token) {
    // Jeton périmé (émis avant l'ajout du claim role, expiré, ou illisible) :
    // plutôt que de laisser partir une requête condamnée à un 401/403 obscur,
    // on purge la session et on renvoie au login immédiatement.
    const payload = decodeJwtPayload(token);
    const expired = !!payload?.exp && payload.exp * 1000 < Date.now();
    const sansRole = !payload?.role && !req.url.includes('/auth/');
    if (!payload || expired || sansRole) {
      ['wydad_token', 'wydad_email', 'wydad_first_name', 'wydad_last_name', 'wydad_role', 'wydad_user_id']
        .forEach((k) => localStorage.removeItem(k));
      inject(Router).navigate(['/login']);
      return throwError(() => new HttpErrorResponse({ url: req.url, status: 401, statusText: 'Session expirée' }));
    }
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: unknown) => {
      // Token invalide ou expire : deconnexion et retour au login.
      // On ignore les appels d'auth eux-memes (mauvais mot de passe = 401 legitime).
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.includes('/auth/login') &&
        !req.url.includes('/auth/register')
      ) {
        localStorage.removeItem('wydad_token');
        localStorage.removeItem('wydad_email');
        localStorage.removeItem('wydad_first_name');
        localStorage.removeItem('wydad_last_name');
        localStorage.removeItem('wydad_role');
        localStorage.removeItem('wydad_user_id');
        const router = inject(Router);
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
