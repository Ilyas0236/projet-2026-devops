import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  nextMatch: any = null;
  articles: any[] = [];
  /** Saison en cours depuis la configuration club (source de verite ADMIN). */
  saison = '';
  /** Plans d'abonnement commercialisés (gérés par l'admin via /admin/abonnements/plans). */
  subscriptionPlans: any[] = [];
  /** Vrai une fois la réponse de l'API reçue (succès OU erreur) — sert à
   *  distinguer l'état "loading" de l'état "0 plan actif". */
  plansLoaded = false;
  /** Vrai si un JWT valide est en localStorage (utilisateur connecté). */
  isLoggedIn = false;
  api = inject(ApiService);
  auth = inject(AuthService);
  router = inject(Router);

  ngOnInit() {
    this.isLoggedIn = this.auth.isTokenValid();

    this.api.getClubSetting('club_info').subscribe({
      next: (info) => {
        this.saison = info?.saison || '';
      },
      error: () => {
        this.saison = '';
      }
    });

    // Fetch upcoming match
    this.api.getEvents().subscribe({
      next: (events) => {
        const upcoming = events.filter(e => e.status === 'UPCOMING');
        if (upcoming.length > 0) {
          upcoming.sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime());
          this.nextMatch = upcoming[0];
        }
      },
      error: () => {
        this.nextMatch = null;
      }
    });

    // Fetch news
    this.api.getArticles().subscribe({
      next: (data) => {
        this.articles = data.slice(0, 3);
      },
      error: () => {
        this.articles = [];
      }
    });

    // Fetch plans d'abonnement actifs (catalogue public)
    this.api.listSubscriptionPlans().subscribe({
      next: (plans) => {
        this.subscriptionPlans = (plans || []).filter(p => p.isActive);
        this.plansLoaded = true;
      },
      error: () => {
        this.subscriptionPlans = [];
        this.plansLoaded = true;
      }
    });
  }

  /**
   * CTA "S'abonner" — si l'utilisateur n'est pas connecté, on le renvoie
   * vers /login avec returnUrl pour qu'il revienne ici après connexion.
   * Sinon, directement sur la page catalogue pour finaliser.
   */
  subscribeTarget(): string {
    return this.isLoggedIn ? '/abonnement' : '/login';
  }

  subscribeQuery(): { returnUrl: string } | null {
    return this.isLoggedIn ? null : { returnUrl: this.router.url };
  }

  formatPrice(value: number | string): string {
    const n = Number(value);
    if (isNaN(n)) return '—';
    // Format simple avec espace fine pour les milliers (5 000 pas 5000).
    return n.toLocaleString('fr-FR', { maximumFractionDigits: 0 });
  }
}
