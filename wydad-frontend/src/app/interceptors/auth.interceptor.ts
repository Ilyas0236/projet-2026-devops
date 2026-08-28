import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, catchError, filter, finalize, first, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

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

/**
 * File d'attente du refresh : un seul appel /auth/refresh à la fois, les
 * requêtes concurrentes attendent le résultat (premier arrivé gagne).
 */
let refreshInProgress$: BehaviorSubject<string | null> | null = null;

/** Purge la session et ramène au login. */
function forceLogout(auth: AuthService, router: Router): void {
  auth.logout();
  router.navigate(['/login']);
}

/** Lit le token SANS déclencher de 401. Retourne null si expiré/absent/illisible. */
function readValidToken(): string | null {
  const t = localStorage.getItem('wydad_token');
  if (!t) return null;
  try {
    const payload = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    if (payload?.exp && payload.exp * 1000 < Date.now()) {
      // Token expiré : on le purge et on continue SANS Bearer, comme
      // un visiteur non authentifié. C'est ce qui évite la redirection
      // systématique vers /login sur les pages publiques (matchs, …).
      ['wydad_token', 'wydad_refresh_token', 'wydad_email',
       'wydad_first_name', 'wydad_last_name', 'wydad_role', 'wydad_user_id']
        .forEach((k) => localStorage.removeItem(k));
      return null;
    }
  } catch {
    return null;
  }
  return t;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = readValidToken();

  // Les appels d'authentification partent toujours sans Bearer et ne
  // déclenchent jamais de refresh (sinon boucle infinie).
  const isAuthCall = req.url.includes('/auth/login')
    || req.url.includes('/auth/register')
    || req.url.includes('/auth/refresh');

  if (token && !isAuthCall) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse)
        || error.status !== 401
        || isAuthCall
        // Mauvais identifiant = 401 légitime, pas de refresh.
        || req.url.includes('/auth/')
      ) {
        return throwError(() => error);
      }

      // ─── 401 sur une requête métier → tentative de reconnexion ───
      const refreshToken = localStorage.getItem('wydad_refresh_token');
      if (!refreshToken) {
        forceLogout(auth, router);
        return throwError(() => error);
      }

      if (!refreshInProgress$) {
        refreshInProgress$ = new BehaviorSubject<string | null>(null);
        auth.refreshSession().pipe(
          first(),
          finalize(() => {
            // Le prochain 401 pourra relancer un refresh.
            setTimeout(() => (refreshInProgress$ = null));
          })
        ).subscribe({
          next: (res: any) => {
            refreshInProgress$?.next(res?.accessToken ?? null);
            refreshInProgress$?.complete();
          },
          error: () => {
            // Refresh refusé (session révoquée, token expiré…) : déconnexion.
            forceLogout(auth, router);
            refreshInProgress$?.next(null);
            refreshInProgress$?.complete();
          }
        });
      }

      return refreshInProgress$.pipe(
        filter((t): t is string => t !== null),
        take(1),
        switchMap((newToken) => next(req.clone({
          setHeaders: { Authorization: `Bearer ${newToken}` }
        })))
      );
    })
  );
};
