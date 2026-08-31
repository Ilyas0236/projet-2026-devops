import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * B.12 — Catalogue d'abonnements saisonniers WAC.
 * Affiche les PLANS (Pelouse, Tribune, Premium, VIP, VVIP...) gérés par
 * l'admin via l'entité SubscriptionPlan (plus de grille hardcodée).
 * Prix regular / adherent selon que l'utilisateur a déjà un abonnement
 * actif. Permet l'achat via un dialog de paiement SIMULÉ.
 *
 * <p>B.18 — Pour un parent connecté : ajout d'un sélecteur de
 * bénéficiaire « pour moi / pour mon fils » au-dessus du dialog de
 * paiement. Voir {@link selectedBeneficiary} pour le modèle et
 * {@link submitPayment} pour l'envoi de {@code beneficiaryAcademyMemberId}.</p>
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

  // B.18 — sélecteur de bénéficiaire (parent uniquement)
  isParent = false;
  myChildren: any[] = [];
  childrenLoading = false;
  /**
   * Bénéficiaire courant : 'self' = achat pour le parent, sinon un enfant
   * identifié par son id AcademyMember.
   */
  selectedBeneficiary: 'self' | number = 'self';

  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);
  router = inject(Router);

  ngOnInit() {
    this.loadData();
    this.loadBeneficiaries();
  }

  /**
   * B.18 — charge les enfants académie du parent connecté pour peupler le
   * sélecteur de bénéficiaire. Silencieux pour les non-parents : le
   * bandeau n'est pas affiché.
   */
  private loadBeneficiaries() {
    if (this.auth.getTokenRole() !== 'PARENT') return;
    this.isParent = true;
    const parentId = this.auth.getCurrentUserId();
    if (!parentId) return;
    this.childrenLoading = true;
    this.api.getMyChildren(parentId).subscribe({
      next: (kids) => {
        this.myChildren = kids || [];
        this.childrenLoading = false;
      },
      error: () => {
        this.myChildren = [];
        this.childrenLoading = false;
      }
    });
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

  /**
   * Libellé du bénéficiaire courant (pour l'afficher dans le dialog).
   * Renvoie "vous-même" pour self, sinon le nom complet de l'enfant.
   */
  beneficiaryLabel(): string {
    if (this.selectedBeneficiary === 'self') return 'vous-même';
    const child = this.myChildren.find(c => c.id === this.selectedBeneficiary);
    return child ? child.childFullName : 'votre enfant';
  }

  openPaymentDialog(plan: any) {
    const email = localStorage.getItem('wydad_email');
    if (!email) {
      this.toast.info('Veuillez vous connecter pour acheter un abonnement.');
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

    const payload: any = {
      planCode: this.selectedPlan.code,
      cardNumber: this.paymentForm.cardNumber,
      expiryDate: this.paymentForm.expiryDate,
      cvv: this.paymentForm.cvv,
      otp: this.paymentForm.otp
    };
    if (this.isParent && this.selectedBeneficiary !== 'self') {
      payload.beneficiaryAcademyMemberId = this.selectedBeneficiary;
    }

    this.api.purchaseSubscription(payload).subscribe({
      next: (sub) => {
        this.paymentLoading = false;
        this.closePaymentDialog();
        const label = sub.planName || sub.zoneDisplayName;
        const forWhom = this.isParent && this.selectedBeneficiary !== 'self'
          ? ` pour ${this.beneficiaryLabel()}`
          : '';
        this.toast.success(`Abonnement ${label}${forWhom} confirmé pour la saison ${sub.season} !`);
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
