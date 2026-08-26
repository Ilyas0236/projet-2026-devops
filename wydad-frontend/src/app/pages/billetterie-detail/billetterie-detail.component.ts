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
  /** Numéro du ticket émis après achat visiteur (pour affichage confirmation). */
  lastTicketNumber: string | null = null;

  // Champs du formulaire visiteur (B.28)
  guest = {
    firstName: '',
    lastName: '',
    email: '',
    phone: ''
  };

  private destroy$ = new Subject<void>();

  api = inject(ApiService);
  auth = inject(AuthService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  ngOnInit() {
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
            next: (profile) => { this.userId = profile?.id ?? null; },
            error: () => { this.userId = null; }
          });
          this.api.getBalance(email).subscribe({
            next: (data) => { this.userBalance = data?.balance || 0; },
            error: () => { this.userBalance = 0; }
          });
        }
      });
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
   * Validation client du formulaire visiteur (B.28). Retourne true si OK,
   * sinon remplit this.errorMsg et renvoie false.
   */
  private validateGuest(): boolean {
    const g = this.guest;
    if (!g.firstName.trim() || !g.lastName.trim()) {
      this.errorMsg = 'Veuillez saisir votre prénom et nom.';
      return false;
    }
    // Email : regex simple mais suffisante
    const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(g.email.trim());
    if (!emailOk) {
      this.errorMsg = 'Adresse e-mail invalide.';
      return false;
    }
    // Téléphone : on accepte tout format international commençant par + et 8-15 chiffres
    const phoneOk = /^\+?\d{8,15}$/.test(g.phone.replace(/[\s.-]/g, ''));
    if (!phoneOk) {
      this.errorMsg = 'Numéro de téléphone invalide (format international attendu).';
      return false;
    }
    return true;
  }

  /**
   * Point d'entrée unique : déclenche l'achat côté membre (purchaseTickets)
   * ou côté visiteur (purchaseAsGuest, B.28) selon l'état de connexion.
   * Les deux flux convergent vers un écran de succès avec n° de billet.
   */
  purchase() {
    if (!this.selectedSection || !this.event) {
      this.errorMsg = 'Veuillez sélectionner une zone.';
      return;
    }
    this.errorMsg = '';
    this.successMsg = '';
    this.processing = true;

    if (this.isLoggedIn) {
      this.purchaseAsMember();
    } else {
      if (!this.validateGuest()) {
        this.processing = false;
        return;
      }
      this.purchaseAsGuest();
    }
  }

  private purchaseAsMember() {
    // Pour le membre, on lit l'email et le nom depuis le localStorage en
    // sécurisant les valeurs nulles (compte créé avant refactor inscription).
    const fn = (localStorage.getItem('wydad_first_name') || '').trim();
    const ln = (localStorage.getItem('wydad_last_name') || '').trim();
    const userEmail = (localStorage.getItem('wydad_email') || '').trim();
    const userFullName = (fn + ' ' + ln).trim() || undefined;

    const req = {
      eventId: this.event.id,
      userId: this.userId,
      userFullName,
      userEmail,
      category: this.selectedSection.category,
      quantity: this.quantity,
      paymentMethod: this.paymentMethod
    };

    this.api.purchaseTickets(req).subscribe({
      next: (res) => this.handlePurchaseSuccess(res),
      error: (err) => this.handlePurchaseError(err)
    });
  }

  private purchaseAsGuest() {
    const req = {
      eventId: this.event.id,
      category: this.selectedSection.category,
      quantity: this.quantity,
      guestFirstName: this.guest.firstName.trim(),
      guestLastName: this.guest.lastName.trim(),
      guestEmail: this.guest.email.trim(),
      guestPhone: this.guest.phone.trim(),
      paymentMethod: this.paymentMethod
    };

    this.api.purchaseAsGuest(req).subscribe({
      next: (res) => this.handlePurchaseSuccess(res),
      error: (err) => this.handlePurchaseError(err)
    });
  }

  /**
   * Centralise le traitement de la réponse : récupère le(s) numéro(s) de
   * billet et affiche l'écran de succès. Redirige vers /profil/billets
   * seulement pour les membres (les visiteurs n'ont pas de compte).
   */
  private handlePurchaseSuccess(res: any) {
    this.processing = false;
    const tickets: any[] = Array.isArray(res) ? res : (res?.tickets || [res]);
    const first = tickets.find((t: any) => t && (t.ticketNumber || t.ticket_number)) || tickets[0];
    this.lastTicketNumber = first?.ticketNumber || first?.ticket_number || null;
    this.successMsg = 'Paiement effectué avec succès !';

    if (this.isLoggedIn) {
      // Membre : redirection vers son espace (authGuard laisse passer)
      this.router.navigate(['/profil/billets']);
    }
    // Visiteur : on reste sur la page et on affiche l'écran de confirmation
    // (voir *ngIf="successMsg" dans le template)
  }

  private handlePurchaseError(err: any) {
    this.processing = false;
    this.errorMsg = err?.error?.message || err?.message || 'Erreur lors de la réservation des billets.';
  }

  /** Bouton "Voir mes billets" depuis l'écran de succès visiteur. */
  viewTicketAfterPurchase() {
    if (this.lastTicketNumber) {
      this.router.navigate(['/profil/billets'], { queryParams: { ticket: this.lastTicketNumber } });
    } else {
      this.router.navigate(['/']);
    }
  }
}
