import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-billetterie-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ErrorBannerComponent],
  templateUrl: './billetterie-detail.component.html',
  styleUrls: ['./billetterie-detail.component.scss']
})
export class BilletterieDetailComponent implements OnInit, OnDestroy {
  event: any = null;
  loading = true;
  loadError = false;
  selectedSection: any = null;
  quantity = 1;
  isLoggedIn = false;
  paymentMethod: 'ECASH' | 'CARD' = 'CARD';
  userBalance = 0;
  userId: number | null = null;
  processing = false;
  errorMsg = '';
  successMsg = '';
  /** Numéro du ticket émis après achat membre (affichage confirmation). */
  lastTicketNumber: string | null = null;

  // B.18 — sélecteur de bénéficiaire pour les parents.
  isParent = false;
  myChildren: any[] = [];
  childrenLoading = false;
  /** 'self' = achat pour le parent connecté, sinon un academyMemberId. */
  selectedBeneficiary: 'self' | number = 'self';

  private destroy$ = new Subject<void>();

  api = inject(ApiService);
  auth = inject(AuthService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  ngOnInit() {
    // Garde-fou B.12 : un visiteur non connecté ne doit PAS pouvoir voir
    // la page détail (sections, prix, plan du stade) — il est renvoyé
    // vers /login avec returnUrl. C'était le "bug grave" remonté par
    // l'utilisateur : "cliquer Acheter m'emmène dans une autre page".
    if (!this.auth.isTokenValid()) {
      const eventId = this.route.snapshot.paramMap.get('id');
      this.router.navigate(['/login'], {
        queryParams: { returnUrl: eventId ? `/billetterie/${eventId}` : '/billetterie' }
      });
      return;
    }

    const eventId = this.route.snapshot.paramMap.get('id');
    if (eventId) {
      this.loadEvent(Number(eventId));
    }

    // Suivre l'état de connexion : on utilise takeUntil + currentUser$ pour
    // éviter l'empilement d'abonnements (F5, login) qui ré-déclenchent
    // getProfile/getBalance en boucle (fuite mémoire + appels en double).
    this.auth.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(email => {
        this.isLoggedIn = !!email;
        this.userId = null;
        this.userBalance = 0;
        if (email) {
          this.auth.getProfile().subscribe({
            next: (profile) => {
              this.userId = profile?.id ?? null;
              this.loadChildrenIfParent();
            },
            error: () => { this.userId = null; }
          });
          this.api.getBalance(email).subscribe({
            next: (data) => { this.userBalance = data?.balance || 0; },
            error: () => { this.userBalance = 0; }
          });
        }
      });
  }

  /**
   * B.18 — si l'utilisateur connecté est PARENT, charge ses enfants
   * académie pour peupler le sélecteur de bénéficiaire. Silencieux
   * pour les non-parents.
   */
  private loadChildrenIfParent() {
    if (this.auth.getTokenRole() !== 'PARENT' || !this.userId) return;
    this.isParent = true;
    this.childrenLoading = true;
    this.api.getMyChildren(this.userId).subscribe({
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

  /** Libellé du bénéficiaire courant (pour l'UI). */
  beneficiaryLabel(): string {
    if (this.selectedBeneficiary === 'self') return 'moi';
    const child = this.myChildren.find(c => c.id === this.selectedBeneficiary);
    return child ? child.childFullName : 'votre enfant';
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadEvent(id: number) {
    this.api.getEventById(id).subscribe({
      next: (data) => {
        this.event = data;
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  retry() {
    const eventId = this.route.snapshot.paramMap.get('id');
    if (eventId) {
      this.loadError = false;
      this.loading = true;
      this.loadEvent(Number(eventId));
    }
  }

  /**
   * B.12 — Fenêtre d'achat prioritaire ADHÉRENT (48h avant ouverture au
   * public) sur un match EXCEPTIONNEL. Côté front, on affiche un bandeau
   * informatif et le bouton d'achat sera refusé côté back si l'utilisateur
   * n'est pas adhérent. Le front n'a pas besoin de connaître l'état
   * d'adhésion : le back est la source de vérité (message 403 explicite).
   */
  isInPriorityWindow(): boolean {
    if (!this.event?.exceptional || !this.event?.eventDate) return false;
    const eventDate = new Date(this.event.eventDate).getTime();
    const publicOpen = eventDate - 48 * 60 * 60 * 1000;
    return Date.now() < publicOpen;
  }

  hoursUntilPublicOpen(): number {
    if (!this.event?.eventDate) return 0;
    const eventDate = new Date(this.event.eventDate).getTime();
    const publicOpen = eventDate - 48 * 60 * 60 * 1000;
    const diff = publicOpen - Date.now();
    return diff > 0 ? Math.ceil(diff / (60 * 60 * 1000)) : 0;
  }

  selectSection(section: any) {
    if (section.availableSeats > 0) {
      this.selectedSection = section;
      this.quantity = 1; // reset
    }
  }

  changeQuantity(delta: number) {
    const newQ = this.quantity + delta;
    if (newQ >= 1 && newQ <= 4 && newQ <= this.selectedSection.availableSeats) {
      this.quantity = newQ;
    }
  }

  getTotal(): number {
    return this.selectedSection ? this.selectedSection.price * this.quantity : 0;
  }

  /**
   * B.12 — Achat MEMBRE uniquement. L'ancien flux visiteur (B.28) a été
   * retiré : tout achat requiert un compte VALIDE. Si l'utilisateur n'est
   * pas connecté, on le renvoie vers la page de connexion avec retour
   * automatique à l'URL courante.
   */
  purchase() {
    if (!this.selectedSection || !this.event) {
      this.errorMsg = 'Veuillez sélectionner une zone.';
      return;
    }
    this.errorMsg = '';
    this.successMsg = '';
    this.processing = true;

    if (!this.isLoggedIn) {
      // Pas connecté → invitation à se connecter (ou s'inscrire)
      this.processing = false;
      // /login (et non /connexion qui n'existe pas — c'est un piège
      // historique qui envoyait l'utilisateur sur une 404).
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.purchaseAsMember();
  }

  private purchaseAsMember() {
    // Pour le membre, on lit l'email et le nom depuis le localStorage en
    // sécurisant les valeurs nulles (compte créé avant refactor inscription).
    const fn = (localStorage.getItem('wydad_first_name') || '').trim();
    const ln = (localStorage.getItem('wydad_last_name') || '').trim();
    const userEmail = (localStorage.getItem('wydad_email') || '').trim();
    const userFullName = (fn + ' ' + ln).trim() || undefined;

    const req: any = {
      eventId: this.event.id,
      userId: this.userId,
      userFullName,
      userEmail,
      category: this.selectedSection.category,
      quantity: this.quantity,
      paymentMethod: this.paymentMethod
    };
    // B.18 — si parent achète pour un fils, on envoie l'id AcademyMember.
    if (this.isParent && this.selectedBeneficiary !== 'self') {
      req.beneficiaryAcademyMemberId = this.selectedBeneficiary;
    }

    this.api.purchaseTickets(req).subscribe({
      next: (res) => this.handlePurchaseSuccess(res),
      error: (err) => this.handlePurchaseError(err)
    });
  }

  /**
   * Centralise le traitement de la réponse : récupère le(s) numéro(s) de
   * billet et redirige vers l'espace membre.
   */
  private handlePurchaseSuccess(res: any) {
    this.processing = false;
    const forWhom = this.isParent && this.selectedBeneficiary !== 'self'
      ? ` pour ${this.beneficiaryLabel()}`
      : '';
    this.successMsg = `Paiement effectué avec succès${forWhom} !`;
    // Membre : redirection vers son espace (authGuard laisse passer)
    this.router.navigate(['/profil/billets']);
  }

  private handlePurchaseError(err: any) {
    this.processing = false;
    this.errorMsg = err?.error?.message || err?.message || 'Erreur lors de la réservation des billets.';
  }
  viewTicketAfterPurchase() {
    if (this.lastTicketNumber) {
      this.router.navigate(['/profil/billets'], { queryParams: { ticket: this.lastTicketNumber } });
    } else {
      this.router.navigate(['/']);
    }
  }
}
