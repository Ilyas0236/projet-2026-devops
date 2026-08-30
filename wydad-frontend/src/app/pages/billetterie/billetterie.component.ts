import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-billetterie',
  standalone: true,
  imports: [CommonModule, ErrorBannerComponent, RouterModule],
  templateUrl: './billetterie.component.html',
  styleUrls: ['./billetterie.component.scss']
})
export class BilletterieComponent implements OnInit {
  events: any[] = [];
  loading = true;
  loadError = false;
  api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);

  retry() {
    this.loadError = false;
    this.ngOnInit();
  }

  /**
   * Vrai si l'utilisateur courant a un JWT valide. Pilote l'affichage
   * du bouton "Acheter" sur le listing (B.12) : un visiteur non connecté
   * NE PEUT PAS acheter un billet — il doit d'abord se connecter (ou
   * s'inscrire). Le bouton est désactivé avec un bandeau d'invitation.
   */
  get isLoggedIn(): boolean {
    return this.auth.isTokenValid();
  }

  /**
   * Clic "Acheter" depuis le listing : si pas connecté, redirection
   * vers /login avec returnUrl (les visiteurs ne peuvent pas voir la
   * page détail billetterie sans compte).
   */
  goToPurchase(eventId: number) {
    if (!this.isLoggedIn) {
      this.router.navigate(['/login'], {
        queryParams: { returnUrl: `/billetterie/${eventId}` }
      });
      return;
    }
    this.router.navigate(['/billetterie', eventId]);
  }

  ngOnInit() {
    this.api.getEvents().subscribe({
      next: (data) => {
        // Filter out completed matches
        this.events = data.filter(e => e.status !== 'COMPLETED' && e.status !== 'CANCELLED');
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  isUpcoming(status: string) {
    return status === 'UPCOMING';
  }
}
