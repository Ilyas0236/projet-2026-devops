import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-billetterie-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent],
  template: `
    <div class="min-h-screen light-page pt-0 pb-24 font-sans">
      <div class="max-w-7xl mx-auto px-6 pt-32" *ngIf="loading">
        <div class="space-y-8">
          <div class="club-skeleton h-48 !rounded-2xl"></div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="club-skeleton h-36"></div>
            <div class="club-skeleton h-36"></div>
          </div>
        </div>
      </div>

      <div class="max-w-7xl mx-auto px-6 pt-32" *ngIf="loadError && !loading">
        <app-error-banner message="Impossible de charger cet événement."
                          detail="Il est peut-être indisponible ou la connexion a échoué." (retry)="retry()" />
      </div>

      <div *ngIf="!loading && event">
        <!-- Event Header : héros Club rouge animé -->
        <div class="club-hero mb-12">
          <div class="max-w-7xl mx-auto px-6 py-14 relative z-10">
            <div class="flex-1 text-center md:text-left">
              <span class="club-badge-gold mb-3 inline-flex !text-white !border-white/40 !bg-white/15">{{ event.competition }}</span>
              <h1 class="club-hero-title text-4xl md:text-5xl uppercase tracking-tight mb-2">
                {{ event.homeTeam }} <span class="text-white/50 mx-2">VS</span> {{ event.awayTeam }}
              </h1>
              <div class="flex flex-wrap items-center justify-center md:justify-start gap-4 text-white/85 text-sm mt-4">
                <span class="flex items-center gap-2">📅 {{ event.eventDate | date:'fullDate':'':'fr' }}</span>
                <span class="flex items-center gap-2">⏰ {{ event.eventDate | date:'HH:mm' }}</span>
                <span class="flex items-center gap-2">🏟️ {{ event.venue }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="max-w-7xl mx-auto px-6 flex flex-col lg:flex-row gap-10">
          <!-- Stade & Sections -->
          <div class="lg:w-2/3">
            <h2 class="text-2xl font-display font-bold uppercase text-ink-primary mb-6 border-b-2 border-wydad-red pb-3">Choix de la Zone</h2>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div *ngFor="let section of event.sections"
                   (click)="selectSection(section)"
                   [class.ring-2]="selectedSection?.id === section.id"
                   [class.ring-wydad-red]="selectedSection?.id === section.id"
                   [class.ring-offset-2]="selectedSection?.id === section.id"
                   class="club-card p-6 cursor-pointer relative overflow-hidden group">

                <div *ngIf="section.availableSeats <= 0" class="absolute inset-0 bg-paper-1/80 backdrop-blur-[2px] flex items-center justify-center z-10">
                  <span class="bg-wydad-red text-white font-bold px-4 py-2 rounded transform -rotate-12 uppercase tracking-widest shadow-glow-red">Complet</span>
                </div>

                <div class="flex justify-between items-start mb-4">
                  <div>
                    <h3 class="font-bold text-lg text-ink-primary group-hover:text-wydad-red transition-colors">{{ section.name }}</h3>
                    <span class="text-xs text-ink-secondary uppercase tracking-wider">{{ section.category }}</span>
                  </div>
                  <div class="text-right">
                    <div class="text-2xl font-black text-wydad-red">{{ section.price }} DH</div>
                  </div>
                </div>

                <div class="w-full bg-paper-2 rounded-full h-2 mt-4 overflow-hidden">
                  <div class="bg-gradient-to-r from-wydad-red to-wydad-red-dark h-2 rounded-full transition-all duration-700" [style.width.%]="(section.capacity - section.availableSeats) / section.capacity * 100"></div>
                </div>
                <div class="text-xs text-ink-tertiary text-right mt-1">{{ section.availableSeats }} places restantes</div>
              </div>
            </div>
          </div>

          <!-- Checkout Sidebar -->
          <div class="lg:w-1/3">
            <div class="club-card p-6 sticky top-32">
              <h2 class="text-xl font-display font-bold uppercase mb-6 text-center text-ink-primary border-b-2 border-wydad-red pb-3">Votre Commande</h2>

              <div *ngIf="!selectedSection" class="text-center py-10 text-ink-tertiary text-sm">
                Veuillez sélectionner une zone du stade pour continuer.
              </div>

              <div *ngIf="selectedSection" class="space-y-6">
                <!-- Recap -->
                <div class="bg-paper-1 rounded-lg p-4 border border-paper-3">
                  <div class="flex justify-between mb-2">
                    <span class="text-ink-secondary">Zone</span>
                    <span class="font-bold text-ink-primary">{{ selectedSection.name }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-ink-secondary">Prix unitaire</span>
                    <span class="font-bold text-ink-primary">{{ selectedSection.price }} DH</span>
                  </div>
                </div>

                <!-- Quantity -->
                <div>
                  <label class="block text-sm text-ink-secondary mb-2">Quantité (Max 4)</label>
                  <div class="flex items-center gap-4 bg-paper-1 p-2 rounded-lg border border-paper-3 w-fit">
                    <button (click)="changeQuantity(-1)" class="w-8 h-8 flex items-center justify-center bg-paper-2 hover:bg-wydad-red hover:text-white rounded text-ink-primary transition-colors">-</button>
                    <span class="font-bold w-4 text-center text-ink-primary">{{ quantity }}</span>
                    <button (click)="changeQuantity(1)" class="w-8 h-8 flex items-center justify-center bg-paper-2 hover:bg-wydad-red hover:text-white rounded text-ink-primary transition-colors">+</button>
                  </div>
                </div>

                <!-- Total -->
                <div class="flex justify-between items-end border-t border-paper-3 pt-4 mt-6">
                  <span class="text-ink-secondary uppercase tracking-wider text-sm">Total à payer</span>
                  <span class="text-3xl font-black text-wydad-red">{{ getTotal() }} DH</span>
                </div>

                <div *ngIf="!isLoggedIn" class="bg-red-50 border border-wydad-red/40 rounded-lg p-4 text-sm text-center text-ink-primary">
                  Vous devez être connecté(e) pour acheter des billets.
                  <button (click)="router.navigate(['/login'])" class="mt-2 text-wydad-red font-bold hover:underline">Se connecter</button>
                </div>

                <div *ngIf="isLoggedIn">
                  <label class="block text-sm text-ink-secondary mb-2">Méthode de paiement</label>
                  <select [(ngModel)]="paymentMethod" class="w-full bg-paper-0 border border-paper-3 rounded-lg p-3 text-ink-primary mb-4 focus:border-wydad-red focus:ring-0">
                    <option value="ECASH">Portefeuille E-Cash (Solde: {{ userBalance }} DH)</option>
                    <option value="CARD">Carte Bancaire (Simulation)</option>
                  </select>

                  <button (click)="purchase()" [disabled]="processing || (paymentMethod === 'ECASH' && userBalance < getTotal())" class="paper-btn-primary w-full py-4 skew-x-[-10deg]">
                    <span class="skew-x-[10deg] block">{{ processing ? 'Traitement...' : 'Confirmer le paiement' }}</span>
                  </button>

                  <p *ngIf="paymentMethod === 'ECASH' && userBalance < getTotal()" class="text-wydad-red-dark text-xs text-center mt-2 font-semibold">Solde insuffisant. Veuillez recharger votre E-Cash.</p>
                  <p *ngIf="errorMsg" class="text-wydad-red-dark text-sm text-center mt-3 font-semibold">{{ errorMsg }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class BilletterieDetailComponent implements OnInit {
  event: any = null;
  loading = true;
  loadError = false;
  selectedSection: any = null;
  quantity = 1;
  isLoggedIn = false;
  paymentMethod = 'ECASH';
  userBalance = 0;
  userId: number | null = null;
  processing = false;
  errorMsg = '';

  api = inject(ApiService);
  auth = inject(AuthService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  ngOnInit() {
    const eventId = this.route.snapshot.paramMap.get('id');
    if (eventId) {
      this.loadEvent(Number(eventId));
    }
    
    this.auth.currentUser$.subscribe(email => {
      this.isLoggedIn = !!email;
      if (email) {
        this.auth.getProfile().subscribe(profile => {
          this.userId = profile.id;
        });
        this.api.getBalance(email).subscribe(data => {
          this.userBalance = data?.balance || 0;
        });
      }
    });
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

  purchase() {
    if (!this.selectedSection || !this.userId) return;
    
    this.processing = true;
    this.errorMsg = '';

    const req = {
      eventId: this.event.id,
      userId: this.userId,
      userFullName: localStorage.getItem('wydad_first_name') + ' ' + localStorage.getItem('wydad_last_name'),
      userEmail: localStorage.getItem('wydad_email'),
      category: this.selectedSection.category,
      quantity: this.quantity,
      paymentMethod: this.paymentMethod
    };

    this.api.purchaseTickets(req).subscribe({
      next: (res) => {
        this.processing = false;
        this.router.navigate(['/profil/billets']);
      },
      error: (err) => {
        this.processing = false;
        this.errorMsg = err.error?.message || "Erreur lors de la réservation des billets.";
      }
    });
  }
}
