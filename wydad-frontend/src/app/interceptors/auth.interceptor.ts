import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('wydad_token');
  if (token) {
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
