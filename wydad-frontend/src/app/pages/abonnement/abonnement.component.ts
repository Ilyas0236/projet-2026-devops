import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

/**
 * B.12 — Catalogue d'abonnements saisonniers WAC.
 * Affiche les 10 zones (Pelouse, Tribune, Premium, VIP, VVIP...) avec
 * les deux prix (regular / adherent) selon que l'utilisateur a déjà un
 * abonnement actif. Permet l'achat via un dialog de paiement SIMULÉ.
 *
 * Côté backend, c'est auth-service /api/auth/subscriptions/* qui pilote
 * (cf. feat/subscription-purchase-flow). Ce composant ne fait QUE
 * consommer ces endpoints.
 */
@Component({
  selector: 'app-abonnement',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './abonnement.component.html',
  styles: [`
    .zone-card { transition: all 0.2s; }
    .zone-card:hover { transform: translateY(-2px); }
  `]
})
export class AbonnementComponent implements OnInit {
  zones: any[] = [];
  activeSubscription: any = null;
  isAdherent = false;
  loading = false;
  showPaymentDialog = false;
  selectedZone: any = null;
  paymentForm = { cardNumber: '', expiryDate: '', cvv: '', otp: '' };
  paymentLoading = false;
  paymentError = '';

  api = inject(ApiService);
  toast = inject(ToastService);
  router = inject(Router);

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.api.listSubscriptionZones().subscribe({
      next: (zones) => this.zones = zones,
      error: () => this.toast.error('Impossible de charger les zones d\'abonnement')
    });
    const email = localStorage.getItem('wydad_email');
    if (email) {
      this.api.getMyActiveSubscription().subscribe({
        next: (sub) => {
          this.activeSubscription = sub;
          this.isAdherent = !!sub;
        },
        error: () => {
          this.activeSubscription = null;
          this.isAdherent = false;
        }
      });
    }
    this.loading = false;
  }

  /** Prix à afficher pour la zone : adherent si l'utilisateur a un abonnement actif. */
  displayPrice(zone: any): number {
    return this.isAdherent ? zone.priceAdherent : zone.priceRegular;
  }

  /** Couleur du badge économie vs 15 billets unitaires. */
  savings(zone: any): number {
    // Estimation économie vs 15 billets à 50 DH chacun = 750 DH.
    // On affichera le prix catalogue barré + économie.
    return Math.max(0, 750 - zone.priceRegular);
  }

  openPaymentDialog(zone: any) {
    const email = localStorage.getItem('wydad_email');
    if (!email) {
      this.toast.info('Veuillez vous connecter pour acheter un abonnement.');
      this.router.navigate(['/login']);
      return;
    }
    this.selectedZone = zone;
    this.paymentForm = { cardNumber: '4242424242424242', expiryDate: '12/29', cvv: '123', otp: '123456' };
    this.paymentError = '';
    this.showPaymentDialog = true;
  }

  closePaymentDialog() {
    this.showPaymentDialog = false;
    this.selectedZone = null;
    this.paymentError = '';
  }

  submitPayment() {
    if (!this.selectedZone) return;
    this.paymentLoading = true;
    this.paymentError = '';

    this.api.purchaseSubscription({
      zoneCode: this.selectedZone.code,
      cardNumber: this.paymentForm.cardNumber,
      expiryDate: this.paymentForm.expiryDate,
      cvv: this.paymentForm.cvv,
      otp: this.paymentForm.otp
    }).subscribe({
      next: (sub) => {
        this.paymentLoading = false;
        this.closePaymentDialog();
        this.toast.success(`Abonnement ${sub.zoneDisplayName} confirmé pour la saison ${sub.season} !`);
        this.loadData();
      },
      error: (err) => {
        this.paymentLoading = false;
        this.paymentError = err.error?.message
          || 'Le paiement a été refusé. Vérifiez votre carte et réessayez.';
      }
    });
  }
}
