import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

/**
 * Forme de la réponse backend GET /api/auth/member-card (cf.
 * auth-service MemberCardResponse — refonte B.12) : la carte est 100%
 * dérivée de l'abonnement saisonnier ACTIF de l'utilisateur. Si pas
 * d'abonnement actif, l'API renvoie 404 et le front affiche le CTA
 * « Acheter mon abonnement ».
 */
export interface MemberCard {
  email: string;
  firstName: string;
  lastName: string;
  planCode: string;
  planName: string;
  season: string;
  validFrom: string;
  validTo: string;
  referralCode: string;
  qrCodeBase64: string;
}

@Component({
  selector: 'app-carte-membre',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './carte-membre.component.html',
  styles: []
})
export class CarteMembreComponent implements OnInit {
  cardData: MemberCard | null = null;
  /** Vrai quand l'API a renvoyé 404 : utilisateur sans abonnement actif. */
  noSubscription = false;
  cardLoading = false;
  pdfLoading = false;

  api = inject(ApiService);
  router = inject(Router);
  toast = inject(ToastService);

  ngOnInit() {
    this.loadCard();
  }

  loadCard() {
    const email = localStorage.getItem('wydad_email');
    if (!email) {
      this.cardData = null;
      this.noSubscription = false;
      return;
    }
    this.cardLoading = true;
    this.noSubscription = false;
    this.api.getMemberCard(email).subscribe({
      next: (data) => {
        this.cardData = data;
        this.noSubscription = false;
        this.cardLoading = false;
      },
      error: (err) => {
        this.cardData = null;
        // 404 = pas d'abonnement actif (cas normal, UX claire)
        this.noSubscription = err?.status === 404;
        this.cardLoading = false;
      }
    });
  }

  downloadAttestation() {
    const email = localStorage.getItem('wydad_email');
    if (!email) return;
    this.pdfLoading = true;
    this.api.getAttestation(email).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'attestation-wac.pdf';
        a.click();
        window.URL.revokeObjectURL(url);
        this.pdfLoading = false;
      },
      error: () => {
        this.toast.error('Erreur lors du téléchargement de l\'attestation.');
        this.pdfLoading = false;
      }
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }

  goAbonnement() {
    this.router.navigate(['/abonnement']);
  }
}
