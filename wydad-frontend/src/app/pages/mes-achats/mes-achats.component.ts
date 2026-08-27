import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * B.12 — Page « Mes achats » : regroupe TOUT l'historique d'achat
 * d'un utilisateur en 3 onglets (Billets match, Abonnement saisonnier,
 * Commandes boutique). Source unique pour le client, à la place des 3
 * pages dispersées /profil/billets + /profil/carte + /profil/commandes.
 */
@Component({
  selector: 'app-mes-achats',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './mes-achats.component.html',
  styles: [`
    .tab-btn { @apply px-4 py-2 font-display uppercase tracking-wider text-sm rounded-t-lg border-b-2 border-transparent transition-colors; }
    .tab-btn.active { @apply border-wydad-red text-wydad-red; }
    .tab-btn:not(.active) { @apply text-ink-secondary hover:text-wydad-red; }
  `]
})
export class MesAchatsComponent implements OnInit {
  tab: 'billets' | 'abonnement' | 'commandes' = 'billets';
  isLoggedIn = false;
  loading = true;

  // Billets
  tickets: any[] = [];
  // Abonnement
  activeSubscription: any = null;
  subscriptionHistory: any[] = [];
  // Commandes
  orders: any[] = [];

  // QR pour billets
  private qrObjectUrls: Record<number, string> = {};
  downloadingId: number | null = null;

  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  ngOnInit() {
    this.auth.currentUser$.subscribe(email => {
      this.isLoggedIn = !!email;
      if (email) {
        this.auth.getProfile().subscribe(profile => this.loadAll(profile.id));
      } else {
        this.loading = false;
      }
    });
  }

  loadAll(userId: number) {
    this.loading = true;
    this.api.getTicketsByUser(userId).subscribe({
      next: (t) => {
        this.tickets = t.sort((a, b) =>
          new Date(b.eventDate).getTime() - new Date(a.eventDate).getTime());
        this.tickets.forEach(tt => this.loadQr(tt.id));
      },
      error: () => { /* silencieux : 0 billets si pas d'achat */ }
    });
    this.api.getMyActiveSubscription().subscribe({
      next: (sub) => { this.activeSubscription = sub; this.loading = false; },
      error: () => { this.activeSubscription = null; this.loading = false; }
    });
    this.api.getMySubscriptionHistory().subscribe({
      next: (h) => { this.subscriptionHistory = h || []; },
      error: () => { this.subscriptionHistory = []; }
    });
    this.api.getMyOrders().subscribe({
      next: (o) => { this.orders = o || []; },
      error: () => { this.orders = []; }
    });
  }

  setTab(t: 'billets' | 'abonnement' | 'commandes') { this.tab = t; }

  // --- Billets ---

  private loadQr(ticketId: number) {
    if (this.qrObjectUrls[ticketId]) return;
    this.api.getTicketQr(ticketId).subscribe({
      next: (blob) => this.qrObjectUrls[ticketId] = URL.createObjectURL(blob),
      error: () => { /* QR indisponible — le n° de billet reste affiché */ }
    });
  }

  qrUrl(ticketId: number): string {
    return this.qrObjectUrls[ticketId] || '';
  }

  downloadTicketPdf(ticketId: number, ticketNumber: string) {
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
      error: () => { this.toast.error('Téléchargement impossible.'); this.downloadingId = null; }
    });
  }

  // --- Abonnement ---

  totalSaved(): number {
    if (!this.activeSubscription) return 0;
    // Économie par rapport à 15 billets unité TRIBUNE (100 DH) — estimation généreuse
    return Math.max(0, 1500 - Number(this.activeSubscription.paidAmount || 0));
  }
}
