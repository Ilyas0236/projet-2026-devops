import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

interface ShopOrder {
  id: number;
  userEmail: string;
  userFullName: string;
  status: string;
  subtotal: number;
  discount: number;
  adherentDiscount: number;
  total: number;
  promoCode: string | null;
  createdAt: string;
  items: OrderItem[];
}

interface TicketRow {
  id: number;
  ticketNumber: string;
  userId: number;
  userEmail: string;
  userFullName: string;
  eventId: number;
  eventTitle: string;
  category: string;
  status: string;
  price: number;
  createdAt: string;
}

interface SubscriptionRow {
  id: number;
  userEmail: string;
  userFullName: string;
  zoneCode: string;
  season: string;
  status: string;
  paidAmount: number;
  paidAt: string;
  validTo: string;
}

interface DonationRow {
  id: number;
  userEmail: string;
  userFullName: string;
  amount: number;
  description: string | null;
  reference: string | null;
  status: string;
  createdAt: string;
}

type TabKey = 'orders' | 'tickets' | 'subs' | 'dons';

interface PageResp<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-admin-achats',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './admin-achats.component.html',
  styles: [`
    .filter-bar {
      background: #1f1f1f;
      border: 1px solid #2c2c2c;
      border-radius: 8px;
      padding: 1rem;
      margin-bottom: 1rem;
    }
    .filter-bar label { color: #b8b8b8; font-size: 0.85rem; }
    .filter-bar input, .filter-bar select {
      background: #121212; color: #e0e0e0; border: 1px solid #3a3a3a;
      padding: 0.4rem 0.6rem; border-radius: 4px; width: 100%;
    }
    .export-btn {
      background: #1e7e34; color: white; border: none; padding: 0.55rem 1rem;
      border-radius: 4px; cursor: pointer; font-weight: 600; font-size: 0.9rem;
    }
    .export-btn:hover { background: #166c2a; }
    .export-btn.pdf { background: #c4302b; }
    .export-btn.pdf:hover { background: #a32522; }
    .export-bar {
      display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;
    }
    table.inv {
      width: 100%; border-collapse: collapse; font-size: 0.9rem;
    }
    table.inv th, table.inv td {
      padding: 0.5rem 0.75rem; text-align: left; border-bottom: 1px solid #2c2c2c;
    }
    table.inv th { color: #c4302b; font-weight: 600; background: #181818; }
    table.inv td { color: #d0d0d0; }
    table.inv tr:hover td { background: #1a1a1a; }
    .badge {
      display: inline-block; padding: 0.15rem 0.5rem; border-radius: 12px;
      font-size: 0.75rem; font-weight: 600;
    }
    .badge-paid { background: #1e7e34; color: white; }
    .badge-pending { background: #d39e00; color: white; }
    .badge-cancelled { background: #6c757d; color: white; }
    .badge-active { background: #c4302b; color: white; }
    .badge-expired { background: #495057; color: white; }
    .pill-15 {
      background: #1e7e34; color: #d4edda; padding: 0.1rem 0.4rem;
      border-radius: 4px; font-size: 0.7rem; font-weight: 700;
    }
    .tab-btn {
      background: #1f1f1f; color: #d0d0d0; border: 1px solid #2c2c2c;
      padding: 0.6rem 1.2rem; cursor: pointer; font-weight: 600;
      border-bottom: 2px solid transparent;
    }
    .tab-btn.active {
      color: #c4302b; border-bottom-color: #c4302b; background: #181818;
    }
    .tab-btn:hover { background: #2a2a2a; }
    .pager button {
      background: #2a2a2a; color: #e0e0e0; border: none; padding: 0.4rem 0.8rem;
      border-radius: 4px; cursor: pointer; margin: 0 0.2rem;
    }
    .pager button:disabled { opacity: 0.4; cursor: not-aligned; }
    .pager span { color: #b8b8b8; margin: 0 0.5rem; }
    .print-header { display: none; }
    .toast {
      position: fixed; bottom: 1.5rem; right: 1.5rem;
      background: #1e7e34; color: white; padding: 0.75rem 1.25rem;
      border-radius: 6px; font-size: 0.9rem; box-shadow: 0 2px 8px rgba(0,0,0,0.4);
      z-index: 9999;
    }

    @media print {
      .filter-bar, .tab-btn, .pager, .export-bar, nav, aside, header, footer { display: none !important; }
      body { background: white !important; color: black !important; }
      table.inv { page-break-inside: auto; width: 100%; }
      table.inv th { background: #ddd !important; color: black !important;
        -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      table.inv td { color: black !important; }
      table.inv tr { page-break-inside: avoid; page-break-after: auto; }
      table.inv thead { display: table-header-group; }
      .print-header {
        display: block !important; text-align: center; margin-bottom: 1rem;
        font-size: 1.2rem; font-weight: 700; color: black !important;
      }
    }
  `]
})
export class AdminAchatsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

  tab: TabKey = 'orders';

  orders: ShopOrder[] = [];
  tickets: TicketRow[] = [];
  subs: SubscriptionRow[] = [];
  donations: DonationRow[] = [];

  loading = false;
  error: string | null = null;
  toastMsg: string | null = null;

  ordersPage = 0;
  ticketsPage = 0;
  subsPage = 0;
  donationsPage = 0;
  pageSize = 20;
  totalOrders = 0;
  totalTickets = 0;
  totalSubs = 0;
  totalDonations = 0;

  filterForm: FormGroup = this.fb.group({
    startDate: [''],
    endDate: [''],
    userEmail: [''],
    productName: [''],
    eventId: ['']
  });

  ngOnInit(): void {
    this.loadAll();
  }

  switchTab(t: TabKey): void {
    this.tab = t;
  }

  reset(): void {
    this.filterForm.reset({ startDate: '', endDate: '', userEmail: '', productName: '', eventId: '' });
    this.ordersPage = 0;
    this.ticketsPage = 0;
    this.subsPage = 0;
    this.donationsPage = 0;
    this.loadAll();
  }

  apply(): void {
    this.ordersPage = 0;
    this.ticketsPage = 0;
    this.subsPage = 0;
    this.donationsPage = 0;
    this.loadAll();
  }

  loadAll(): void {
    this.loadOrders();
    this.loadTickets();
    this.loadSubs();
    this.loadDonations();
  }

  private getBaseParams(page: number, extra: { [k: string]: string } = {}): HttpParams {
    const f = this.filterForm.value;
    let p = new HttpParams()
      .set('page', String(page))
      .set('size', String(this.pageSize));
    if (f.startDate) p = p.set('startDate', f.startDate);
    if (f.endDate) p = p.set('endDate', f.endDate);
    if (f.userEmail) p = p.set('userEmail', f.userEmail);
    Object.entries(extra).forEach(([k, v]) => { if (v) p = p.set(k, v); });
    return p;
  }

  loadOrders(): void {
    this.loading = true;
    this.error = null;
    const f = this.filterForm.value;
    const params = this.getBaseParams(this.ordersPage, { productName: f.productName || '' });
    this.http.get<PageResp<ShopOrder>>(
      `${environment.apiBaseUrl}/shop/orders/filter`,
      { params }
    ).subscribe({
      next: r => {
        this.orders = r.content;
        this.totalOrders = r.totalElements;
        this.loading = false;
      },
      error: e => {
        this.error = 'Erreur chargement commandes : ' + (e.error?.message || e.message);
        this.loading = false;
      }
    });
  }

  loadTickets(): void {
    const f = this.filterForm.value;
    const params = this.getBaseParams(this.ticketsPage, { eventId: f.eventId || '' });
    this.http.get<PageResp<TicketRow>>(
      `${environment.apiBaseUrl}/ticket/tickets/admin/filter`,
      { params }
    ).subscribe({
      next: r => {
        this.tickets = r.content;
        this.totalTickets = r.totalElements;
      },
      error: e => console.error('Tickets', e)
    });
  }

  loadSubs(): void {
    const params = this.getBaseParams(this.subsPage);
    this.http.get<PageResp<SubscriptionRow>>(
      `${environment.apiBaseUrl}/auth/subscriptions/admin/filter`,
      { params }
    ).subscribe({
      next: r => {
        this.subs = r.content;
        this.totalSubs = r.totalElements;
      },
      error: e => console.error('Subs', e)
    });
  }

  loadDonations(): void {
    const params = this.getBaseParams(this.donationsPage);
    this.http.get<PageResp<DonationRow>>(
      `${environment.apiBaseUrl}/payment/admin/donations/filter`,
      { params }
    ).subscribe({
      next: r => {
        this.donations = r.content;
        this.totalDonations = r.totalElements;
      },
      error: e => console.error('Donations', e)
    });
  }

  goOrdersPage(d: number): void {
    this.ordersPage = Math.max(0, this.ordersPage + d);
    this.loadOrders();
  }
  goTicketsPage(d: number): void {
    this.ticketsPage = Math.max(0, this.ticketsPage + d);
    this.loadTickets();
  }
  goSubsPage(d: number): void {
    this.subsPage = Math.max(0, this.subsPage + d);
    this.loadSubs();
  }
  goDonationsPage(d: number): void {
    this.donationsPage = Math.max(0, this.donationsPage + d);
    this.loadDonations();
  }

  badgeClass(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PAID' || s === 'ACTIVE' || s === 'CONFIRMED' || s === 'DELIVERED' || s === 'SUCCESS') return 'badge badge-paid';
    if (s === 'PENDING' || s === 'PROCESSING') return 'badge badge-pending';
    if (s === 'CANCELLED' || s === 'REFUNDED' || s === 'EXPIRED' || s === 'FAILED') return 'badge badge-cancelled';
    if (s === 'USED') return 'badge badge-active';
    return 'badge badge-cancelled';
  }

  /** Label FR de l'onglet pour les exports / impression. */
  tabLabel(t: TabKey): string {
    switch (t) {
      case 'orders':  return 'Commandes boutique';
      case 'tickets': return 'Billets';
      case 'subs':    return 'Abonnements';
      case 'dons':    return 'Dons';
    }
  }

  /** Mois courant ou du filtre, format YYYY-MM (nom de fichier + en-tête print). */
  monthLabelNow(): string {
    const f = this.filterForm.value;
    if (f.startDate) return f.startDate.slice(0, 7);
    return new Date().toISOString().slice(0, 7);
  }

  /** Variante privée conservée pour usage interne (alias). */
  private monthLabel(): string { return this.monthLabelNow(); }

  private csvEscape(v: any): string {
    const s = v == null ? '' : String(v);
    return /[",;\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  }

  /**
   * Export CSV client-side à partir des tableaux déjà chargés en mémoire.
   * Séparateur ; (Excel FR), BOM UTF-8 pour accents.
   */
  downloadCsv(t: TabKey): void {
    let headers: string[] = [];
    let rows: any[][] = [];

    if (t === 'orders') {
      headers = ['#', 'Date', 'Utilisateur', 'Email', 'Produits', 'Sous-total (DH)', 'Remise (DH)', 'Total (DH)', 'Statut'];
      rows = this.orders.map(o => [
        o.id, o.createdAt, o.userFullName, o.userEmail,
        (o.items || []).map(i => `${i.productName} x${i.quantity}`).join(' | '),
        o.subtotal, o.discount, o.total, o.status
      ]);
    } else if (t === 'tickets') {
      headers = ['#Billet', 'Date', 'Utilisateur', 'Email', 'Match', 'Catégorie', 'Prix (DH)', 'Statut'];
      rows = this.tickets.map(tk => [
        tk.ticketNumber, tk.createdAt, tk.userFullName, tk.userEmail,
        tk.eventTitle, tk.category, tk.price, tk.status
      ]);
    } else if (t === 'subs') {
      headers = ['#', 'Payé le', 'Utilisateur', 'Email', 'Zone', 'Saison', 'Montant (DH)', 'Validité', 'Statut'];
      rows = this.subs.map(s => [
        s.id, s.paidAt, s.userFullName, s.userEmail,
        s.zoneCode, s.season, s.paidAmount, s.validTo, s.status
      ]);
    } else if (t === 'dons') {
      headers = ['#', 'Date', 'Donateur', 'Email', 'Montant (DH)', 'Description', 'Référence', 'Statut'];
      rows = this.donations.map(d => [
        d.id, d.createdAt, d.userFullName, d.userEmail,
        d.amount, d.description, d.reference, d.status
      ]);
    }

    const csv = '﻿' + [headers, ...rows]
      .map(r => r.map(c => this.csvEscape(c)).join(';'))
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `inventaire-wac-${t}-${this.monthLabel()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.showToast(`Inventaire ${t} (${rows.length} lignes) téléchargé.`);
  }

  /**
   * Impression PDF : déclenche window.print() après un tick pour laisser
   * Angular re-rendre. Le CSS @media print formate un rapport propre
   * (cf. styles du composant).
   */
  downloadPdf(t: TabKey): void {
    this.tab = t;
    setTimeout(() => window.print(), 50);
  }

  private showToast(msg: string): void {
    this.toastMsg = msg;
    setTimeout(() => { this.toastMsg = null; }, 3500);
  }
}
