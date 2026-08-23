import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Router, RouterModule } from '@angular/router';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-mes-billets',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  template: `
    <div class="page-header">
      <h1>🎫 Mes Billets</h1>
      <p>Vos accès aux matchs et événements du Wydad AC</p>
    </div>

    <div class="container mx-auto max-w-7xl px-4 py-8" *ngIf="isLoggedIn; else notConnected">
      <div class="flex flex-col md:flex-row gap-8">
        
        <!-- Sidebar Profil -->
        <div class="w-full md:w-1/4">
          <div class="bg-white rounded-xl shadow-sm p-6 border-t-4 border-red-700">
            <h3 class="font-bold text-gray-800 text-lg mb-4">Mon Espace</h3>
            <div class="flex flex-col gap-2">
              <a routerLink="/profil" class="py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium">⚙️ Paramètres du compte</a>
              <a routerLink="/profil/carte" class="py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium">🎟️ Ma Carte Membre</a>
              <a routerLink="/profil/ecash" class="py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium">💰 Porte-Monnaie E-Cash</a>
              <a routerLink="/profil/billets" class="py-2 px-3 rounded bg-red-50 text-red-700 font-bold">🎫 Mes Billets</a>
              <a routerLink="/profil/commandes" class="py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium">🛍️ Mes Commandes</a>
            </div>
          </div>
        </div>

        <!-- Contenu Billets -->
        <div class="w-full md:w-3/4">
          
          <app-error-banner *ngIf="loadError && !loading" message="Impossible de charger vos billets."
                            detail="Vérifiez votre connexion et réessayez." (retry)="retry()" />

          <div *ngIf="loading" class="text-center py-10 text-gray-500">
            Chargement de vos billets...
          </div>

          <div *ngIf="!loading && tickets.length === 0" class="bg-white rounded-xl shadow-sm p-12 text-center border border-gray-100">
            <div class="text-6xl mb-4">🎫</div>
            <h2 class="text-2xl font-bold text-gray-800 mb-2">Aucun billet trouvé</h2>
            <p class="text-gray-500 mb-6">Vous n'avez pas encore acheté de billets pour les prochains matchs.</p>
            <button routerLink="/billetterie" class="bg-red-700 hover:bg-red-800 text-white font-bold py-3 px-8 rounded-full transition-colors shadow-md">
              Voir la Billetterie
            </button>
          </div>

          <div *ngIf="!loading && tickets.length > 0" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-2 gap-6">
            
            <div *ngFor="let ticket of tickets" class="bg-white rounded-xl shadow-md overflow-hidden border border-gray-100 flex flex-col hover:shadow-lg transition-shadow">
              
              <!-- Ticket Header -->
              <div class="bg-red-700 text-white p-4 flex justify-between items-center">
                <span class="font-bold uppercase tracking-wider text-sm">{{ ticket.category }}</span>
                <span class="bg-white/20 px-3 py-1 rounded text-xs font-bold">{{ ticket.status }}</span>
              </div>
              
              <!-- Ticket Body -->
              <div class="p-6 flex-1 flex flex-col justify-between">
                <div>
                  <h3 class="font-black text-xl text-gray-800 mb-1 leading-tight">{{ ticket.eventTitle }}</h3>
                  <div class="text-sm text-gray-500 mb-4">{{ ticket.eventDate | date:'fullDate':'':'fr' }} - {{ ticket.venue }}</div>
                  
                  <div class="grid grid-cols-2 gap-4 bg-gray-50 p-4 rounded-lg border border-gray-200 mb-6">
                    <div>
                      <span class="block text-xs text-gray-500 uppercase">Zone</span>
                      <strong class="text-gray-800">{{ ticket.sectionName }}</strong>
                    </div>
                    <div>
                      <span class="block text-xs text-gray-500 uppercase">Prix</span>
                      <strong class="text-gray-800">{{ ticket.price }} DH</strong>
                    </div>
                  </div>
                </div>
                
                <div class="flex items-center gap-3">
                  <div class="w-16 h-16 bg-gray-100 rounded-lg p-1 border border-gray-200 flex-shrink-0">
                    <img *ngIf="ticket.qrCodeData" [src]="'https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=' + ticket.qrCodeData" alt="QR Code" class="w-full h-full object-cover">
                    <!-- Note: In production, backend should return base64 QR, using external API for demo -->
                  </div>
                  <div class="flex-1">
                    <span class="block text-xs text-gray-500 uppercase">N° Billet</span>
                    <code class="text-sm font-bold text-gray-700 break-all">{{ ticket.ticketNumber }}</code>
                  </div>
                </div>
              </div>
              
              <!-- Ticket Footer -->
              <div class="border-t border-gray-100 p-4 bg-gray-50 flex gap-3">
                <button (click)="downloadPdf(ticket.id, ticket.ticketNumber)" [disabled]="downloadingId === ticket.id" class="flex-1 bg-gray-800 hover:bg-black text-white py-2 rounded font-medium text-sm transition-colors flex items-center justify-center gap-2 disabled:opacity-50">
                  <span *ngIf="downloadingId !== ticket.id">⬇️ Télécharger PDF</span>
                  <span *ngIf="downloadingId === ticket.id">⏳ Chargement...</span>
                </button>
              </div>

            </div>
          </div>

        </div>
      </div>
    </div>

    <ng-template #notConnected>
      <div class="container mx-auto text-center py-20 px-4">
        <div class="text-6xl mb-4">🔒</div>
        <h2 class="text-2xl font-bold text-gray-800 mb-2">Espace sécurisé</h2>
        <p class="text-gray-500 mb-6">Veuillez vous connecter pour voir vos billets.</p>
        <button routerLink="/login" class="bg-red-700 hover:bg-red-800 text-white font-bold py-3 px-8 rounded-full transition-colors shadow-md">
          Se connecter
        </button>
      </div>
    </ng-template>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(90deg, #b71c1c, #8e0000);
      color: white;
      padding: 3rem 2rem;
      text-align: center;
    }
    .page-header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; font-weight: 900; }
    .page-header p { opacity: 0.9; font-size: 1.1rem; }
  `]
})
export class MesBilletsComponent implements OnInit {
  tickets: any[] = [];
  loading = true;
  loadError = false;
  isLoggedIn = false;
  downloadingId: number | null = null;
  
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  ngOnInit() {
    this.auth.currentUser$.subscribe(email => {
      this.isLoggedIn = !!email;
      if (email) {
        this.auth.getProfile().subscribe(profile => {
          this.loadTickets(profile.id);
        });
      }
    });
  }

  loadTickets(userId: number) {
    this.api.getTicketsByUser(userId).subscribe({
      next: (data) => {
        // Trier par date d'événement, les plus récents/à venir en premier
        this.tickets = data.sort((a, b) => new Date(b.eventDate).getTime() - new Date(a.eventDate).getTime());
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  retry() {
    this.loadError = false;
    this.loading = true;
    this.auth.currentUser$.subscribe(email => {
      if (email) {
        this.auth.getProfile().subscribe(profile => {
          this.loadTickets(profile.id);
        });
      }
    });
  }

  downloadPdf(ticketId: number, ticketNumber: string) {
    this.downloadingId = ticketId;
    this.api.getTicketPdf(ticketId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `billet-wac-${ticketNumber}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.downloadingId = null;
      },
      error: () => {
        this.toast.error('Erreur lors du téléchargement du billet.');
        this.downloadingId = null;
      }
    });
  }
}
