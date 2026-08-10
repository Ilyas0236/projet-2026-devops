import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-billetterie-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-wydad-dark text-white pt-32 pb-24 font-sans">
      <div class="max-w-7xl mx-auto px-6" *ngIf="loading">
        <div class="text-center py-20 text-gray-400">Chargement de l'événement...</div>
      </div>

      <div class="max-w-7xl mx-auto px-6" *ngIf="!loading && event">
        <!-- Event Header -->
        <div class="bg-gradient-to-r from-wydad-red/20 to-black/40 border border-white/10 rounded-2xl p-8 mb-10 flex flex-col md:flex-row items-center gap-8 relative overflow-hidden">
          <div class="absolute top-0 right-0 w-64 h-64 bg-wydad-red/10 rounded-full blur-3xl"></div>
          
          <div class="flex-1 text-center md:text-left z-10">
            <span class="text-wydad-gold font-bold tracking-widest uppercase text-xs mb-2 block">{{ event.competition }}</span>
            <h1 class="text-4xl md:text-5xl font-display font-black uppercase tracking-tight mb-2">
              {{ event.homeTeam }} <span class="text-gray-500 mx-2">VS</span> {{ event.awayTeam }}
            </h1>
            <div class="flex flex-wrap items-center justify-center md:justify-start gap-4 text-gray-300 text-sm mt-4">
              <span class="flex items-center gap-2"><span class="text-wydad-red">📅</span> {{ event.eventDate | date:'fullDate':'':'fr' }}</span>
              <span class="flex items-center gap-2"><span class="text-wydad-red">⏰</span> {{ event.eventDate | date:'HH:mm' }}</span>
              <span class="flex items-center gap-2"><span class="text-wydad-red">🏟️</span> {{ event.venue }}</span>
            </div>
          </div>
        </div>

        <div class="flex flex-col lg:flex-row gap-10">
          <!-- Stade & Sections -->
          <div class="lg:w-2/3">
            <h2 class="text-2xl font-display font-bold uppercase mb-6 border-b border-white/10 pb-4">Choix de la Zone</h2>
            
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div *ngFor="let section of event.sections" 
                   (click)="selectSection(section)"
                   [class.ring-2]="selectedSection?.id === section.id"
                   [class.ring-wydad-red]="selectedSection?.id === section.id"
                   class="bg-white/5 border border-white/10 rounded-xl p-6 cursor-pointer hover:bg-white/10 transition-all relative overflow-hidden group">
                
                <div *ngIf="section.availableSeats <= 0" class="absolute inset-0 bg-black/60 backdrop-blur-[2px] flex items-center justify-center z-10">
                  <span class="bg-red-900 text-white font-bold px-4 py-2 rounded transform -rotate-12 uppercase tracking-widest border border-red-500">Complet</span>
                </div>

                <div class="flex justify-between items-start mb-4">
                  <div>
                    <h3 class="font-bold text-lg text-white group-hover:text-wydad-red transition-colors">{{ section.name }}</h3>
                    <span class="text-xs text-gray-400 uppercase tracking-wider">{{ section.category }}</span>
                  </div>
                  <div class="text-right">
                    <div class="text-2xl font-black text-wydad-gold">{{ section.price }} DH</div>
                  </div>
                </div>
                
                <div class="w-full bg-black/50 rounded-full h-2 mt-4 overflow-hidden">
                  <div class="bg-wydad-red h-2 rounded-full" [style.width.%]="(section.capacity - section.availableSeats) / section.capacity * 100"></div>
                </div>
                <div class="text-xs text-gray-400 text-right mt-1">{{ section.availableSeats }} places restantes</div>
              </div>
            </div>
          </div>

          <!-- Checkout Sidebar -->
          <div class="lg:w-1/3">
            <div class="bg-white/5 border border-white/10 rounded-2xl p-6 sticky top-32">
              <h2 class="text-xl font-display font-bold uppercase mb-6 text-center border-b border-white/10 pb-4">Votre Commande</h2>
              
              <div *ngIf="!selectedSection" class="text-center py-10 text-gray-400 text-sm">
                Veuillez sélectionner une zone du stade pour continuer.
              </div>

              <div *ngIf="selectedSection" class="space-y-6">
                <!-- Recap -->
                <div class="bg-black/40 rounded-lg p-4 border border-white/5">
                  <div class="flex justify-between mb-2">
                    <span class="text-gray-400">Zone</span>
                    <span class="font-bold">{{ selectedSection.name }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-gray-400">Prix unitaire</span>
                    <span class="font-bold">{{ selectedSection.price }} DH</span>
                  </div>
                </div>

                <!-- Quantity -->
                <div>
                  <label class="block text-sm text-gray-400 mb-2">Quantité (Max 4)</label>
                  <div class="flex items-center gap-4 bg-black/40 p-2 rounded-lg border border-white/5 w-fit">
                    <button (click)="changeQuantity(-1)" class="w-8 h-8 flex items-center justify-center bg-white/10 hover:bg-wydad-red rounded text-white transition-colors">-</button>
                    <span class="font-bold w-4 text-center">{{ quantity }}</span>
                    <button (click)="changeQuantity(1)" class="w-8 h-8 flex items-center justify-center bg-white/10 hover:bg-wydad-red rounded text-white transition-colors">+</button>
                  </div>
                </div>

                <!-- Total -->
                <div class="flex justify-between items-end border-t border-white/10 pt-4 mt-6">
                  <span class="text-gray-400 uppercase tracking-wider text-sm">Total à payer</span>
                  <span class="text-3xl font-black text-wydad-gold">{{ getTotal() }} DH</span>
                </div>

                <div *ngIf="!isLoggedIn" class="bg-wydad-red/20 border border-wydad-red/50 rounded-lg p-4 text-sm text-center">
                  Vous devez être connecté(e) pour acheter des billets.
                  <button (click)="router.navigate(['/login'])" class="mt-2 text-wydad-red font-bold hover:underline">Se connecter</button>
                </div>

                <div *ngIf="isLoggedIn">
                  <label class="block text-sm text-gray-400 mb-2">Méthode de paiement</label>
                  <select [(ngModel)]="paymentMethod" class="w-full bg-black/50 border border-white/20 rounded-lg p-3 text-white mb-4 focus:border-wydad-red focus:ring-0">
                    <option value="ECASH">Portefeuille E-Cash (Solde: {{ userBalance }} DH)</option>
                    <option value="CARD">Carte Bancaire (Simulation)</option>
                  </select>

                  <button (click)="purchase()" [disabled]="processing || (paymentMethod === 'ECASH' && userBalance < getTotal())" class="w-full py-4 bg-wydad-red hover:bg-red-700 text-white font-display uppercase font-bold tracking-wider rounded-none skew-x-[-10deg] transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed">
                    <span class="skew-x-[10deg] block">{{ processing ? 'Traitement...' : 'Confirmer le paiement' }}</span>
                  </button>
                  
                  <p *ngIf="paymentMethod === 'ECASH' && userBalance < getTotal()" class="text-red-500 text-xs text-center mt-2">Solde insuffisant. Veuillez recharger votre E-Cash.</p>
                  <p *ngIf="errorMsg" class="text-red-500 text-sm text-center mt-3">{{ errorMsg }}</p>
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
        this.loading = false;
      }
    });
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
      quantity: this.quantity
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
