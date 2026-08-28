import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  loading = false;
  error = '';
  token = '';
  /**
   * URL de retour posée par la page d'origine (ex. /abonnement,
   * /billetterie/12) via le query param {@code returnUrl}. Sanitisée
   * pour bloquer les open-redirects (//evil.com, javascript:, etc.).
   * {@code null} → fallback sur la route par rôle.
   */
  returnUrl: string | null = null;

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.returnUrl = this.sanitize(params['returnUrl']);
    });
  }

  /**
   * N'accepte que des chemins relatifs commençant par "/" mais pas par
   * "//" (URL absolue) ni par "javascript:" (XSS via location.href).
   * Renvoie {@code null} si l'URL est malveillante ou absente.
   */
  private sanitize(raw: unknown): string | null {
    if (typeof raw !== 'string' || raw.length === 0) return null;
    if (!raw.startsWith('/')) return null;
    // Bloque les URLs absolues masquées : //evil.com, /\\evil.com, etc.
    if (raw.startsWith('//') || raw.startsWith('/\\')) return null;
    // Bloque les protocoles dangereux au cas où le routeur les laisserait passer
    if (/^\/[a-z][a-z0-9+\-.]*:/i.test(raw)) return null;
    return raw;
  }

  /** Destination par défaut si returnUrl est null OU si le rôle
   *  n'a pas le droit d'accéder à la returnUrl. Étendu à mesure
   *  que de nouveaux rôles sont ajoutés côté backend. */
  private defaultRouteByRole(): string {
    switch (this.authService.getRole()) {
      case 'ADMIN':     return '/admin';
      case 'JOUEUR':    return '/joueur/dashboard';
      case 'STAFF':     return '/staff/dashboard';
      case 'ENTRAINEUR':return '/entraineur/dashboard';
      case 'JOURNALISTE': return '/journaliste/accueil';
      case 'PRESIDENT': return '/president/dashboard';
      case 'PARENT':    return '/academie/mes-enfants';
      default:          return '/profil/carte';
    }
  }

  /** Renvoie la returnUrl sanitisée si elle existe, sinon la route par rôle. */
  private computeDestination(): string {
    return this.returnUrl ?? this.defaultRouteByRole();
  }

  login() {
    this.loading = true;
    this.error = '';
    this.authService.login(this.email, this.password).subscribe({
      next: () => {
        this.loading = false;
        // Priorité : returnUrl sanitisé > défaut par rôle.
        this.router.navigateByUrl(this.computeDestination());
      },
      error: (err) => {
        this.error = err.error?.message || 'Erreur de connexion';
        this.loading = false;
      }
    });
  }
}
