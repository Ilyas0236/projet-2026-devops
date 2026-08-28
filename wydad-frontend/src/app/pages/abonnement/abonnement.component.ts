import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

/**
 * B.12 — Catalogue d'abonnements saisonniers WAC.
 * Affiche les PLANS (Pelouse, Tribune, Premium, VIP, VVIP...) gérés par
 * l'admin via l'entité SubscriptionPlan (plus de grille hardcodée).
 * Prix regular / adherent selon que l'utilisateur a déjà un abonnement
 * actif. Permet l'achat via un dialog de paiement SIMULÉ.
 *
 * Côté backend, c'est auth-service /api/auth/subscriptions/plans qui
 * pilote la grille (cf. Lot 2 SubscriptionPlan JPA). Ce composant ne
 * fait QUE consommer ces endpoints.
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
  plans: any[] = [];
  activeSubscription: any = null;
  isAdherent = false;
  loading = false;
  showPaymentDialog = false;
  selectedPlan: any = null;
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
    this.api.listSubscriptionPlans().subscribe({
      next: (plans) => {
        this.plans = plans;
        this.loading = false;
      },
      error: () => {
        this.toast.error('Impossible de charger les plans d\'abonnement');
        this.loading = false;
      }
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
  }

  /** Prix à afficher pour le plan : adherent si l'utilisateur a déjà un abonnement actif. */
  displayPrice(plan: any): number {
    const regular = Number(plan.regularPrice);
    const adherent = Number(plan.adherentPrice);
    return this.isAdherent ? adherent : regular;
  }

  /** Économie en % quand le prix adhérent est strictement inférieur. */
  adherentDiscountPct(plan: any): number {
    const regular = Number(plan.regularPrice);
    const adherent = Number(plan.adherentPrice);
    if (regular <= 0 || adherent >= regular) return 0;
    return Math.round(((regular - adherent) / regular) * 100);
  }

  openPaymentDialog(plan: any) {
    const email = localStorage.getItem('wydad_email');
    if (!email) {
      this.toast.info('Veuillez vous connecter pour acheter un abonnement.');
      // returnUrl pour que le login ramène l'utilisateur ici.
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.selectedPlan = plan;
    this.paymentForm = { cardNumber: '4242424242424242', expiryDate: '12/29', cvv: '123', otp: '123456' };
    this.paymentError = '';
    this.showPaymentDialog = true;
  }

  closePaymentDialog() {
    this.showPaymentDialog = false;
    this.selectedPlan = null;
    this.paymentError = '';
  }

  submitPayment() {
    if (!this.selectedPlan) return;
    this.paymentLoading = true;
    this.paymentError = '';

    this.api.purchaseSubscription({
      planCode: this.selectedPlan.code,
      cardNumber: this.paymentForm.cardNumber,
      expiryDate: this.paymentForm.expiryDate,
      cvv: this.paymentForm.cvv,
      otp: this.paymentForm.otp
    }).subscribe({
      next: (sub) => {
        this.paymentLoading = false;
        this.closePaymentDialog();
        const label = sub.planName || sub.zoneDisplayName;
        this.toast.success(`Abonnement ${label} confirmé pour la saison ${sub.season} !`);
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
