import { Component, HostListener, OnInit } from '@angular/core';
import { Router, RouterOutlet, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastContainerComponent } from '../../components/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog.component';
import { NotificationBellComponent } from '../../components/notification-bell/notification-bell.component';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, FormsModule, ToastContainerComponent, ConfirmDialogComponent, NotificationBellComponent],
  templateUrl: './public-layout.component.html',
  styleUrls: ['./public-layout.component.scss']
})
export class PublicLayoutComponent implements OnInit {
  isScrolled = false;
  isMobileMenuOpen = false;

  /** État connecté du header (pages consommateur : profil, panier…). */
  isLoggedIn = false;
  firstName: string | null = null;

  /**
   * Rôles internes (joueur / journaliste / entraîneur / staff / parent /
   * président) : ils n'ont pas accès à l'expérience "supporter" (billetterie,
   * abonnement, boutique, dons). On leur cache la navbar publique et le bloc
   * auth pour qu'ils restent dans leur espace dédié — seule la déconnexion
   * leur permet de revenir au site public. ADMIN conserve la navbar.
   */
  get isInternalRole(): boolean {
    const role = this.auth.getTokenRole();
    return role === 'JOUEUR' || role === 'JOURNALISTE'
        || role === 'ENTRAINEUR' || role === 'STAFF'
        || role === 'PARENT' || role === 'PRESIDENT';
  }

  // Coordonnees du club — source de verite : configuration club (ADMIN)
  clubInfo: any = null;

  // Sponsors actifs (B.7) et reseaux sociaux officiels (B.9) —
  // source de verite : ADMIN via content-service
  sponsors: any[] = [];
  socialLinks: any[] = [];

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private router: Router
  ) {}

  /** Lien « Mon espace » selon le rôle du compte connecté. */
  get espaceLink(): string {
    switch (this.auth.getTokenRole()) {
      case 'JOUEUR': return '/joueur/dashboard';
      case 'ENTRAINEUR': return '/entraineur/dashboard';
      case 'STAFF': return '/staff/dashboard';
      case 'PRESIDENT': return '/president/dashboard';
      case 'JOURNALISTE': return '/journaliste/accueil';
      case 'PARENT': return '/academie/mes-enfants';
      case 'ADMIN': return '/admin';
      default: return '/profil';
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  ngOnInit() {
    // État connecté du header (pages consommateur sous layout public).
    this.isLoggedIn = !!this.auth.currentUserValue && this.auth.isTokenValid();
    this.firstName = localStorage.getItem('wydad_first_name');
    this.auth.currentUser$.subscribe(() => {
      this.isLoggedIn = !!this.auth.currentUserValue && this.auth.isTokenValid();
      this.firstName = localStorage.getItem('wydad_first_name');
    });

    this.api.getClubSetting('club_info').subscribe({
      next: (info) => (this.clubInfo = info),
      error: () => (this.clubInfo = null)
    });

    this.api.getSponsorsPublic().subscribe({
      next: (list) => (this.sponsors = list || []),
      error: () => (this.sponsors = [])
    });

    this.api.getClubSetting('social_links').subscribe({
      next: (links) => (this.socialLinks = Array.isArray(links) ? links : []),
      error: () => (this.socialLinks = [])
    });
  }

  /** Initiale affichée sur l'icône sociale (ex "FACEBOOK" -> "F"). */
  socialInitial(platform: string): string {
    return (platform || '?').charAt(0).toUpperCase();
  }

  // Newsletter publique — inscription anonyme (notification-service).
  newsletterEmail = '';
  newsletterBusy = false;
  newsletterDone = false;
  newsletterMessage = '';
  newsletterError = '';

  subscribeNewsletter() {
    const email = (this.newsletterEmail || '').trim();
    if (!email || this.newsletterBusy) return;
    this.newsletterBusy = true;
    this.newsletterError = '';
    this.api.subscribeNewsletter(email).subscribe({
      next: (res) => {
        this.newsletterBusy = false;
        this.newsletterDone = true;
        this.newsletterMessage = res?.message || 'Inscription confirmée. Merci !';
      },
      error: (err) => {
        this.newsletterBusy = false;
        this.newsletterError =
          err?.error?.message || "Échec de l'inscription — vérifiez votre adresse.";
      }
    });
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.isScrolled = window.scrollY > 50;
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
