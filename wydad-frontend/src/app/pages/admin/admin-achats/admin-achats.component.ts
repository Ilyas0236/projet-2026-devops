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
    .pager button:disabled { opacity: 0.4; cursor: not-allowed; }
    .pager span { color: #b8b8b8; margin: 0 0.5rem; }
  `]
})
export class AdminAchatsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

  tab: 'orders' | 'tickets' | 'subs' = 'orders';

  orders: ShopOrder[] = [];
  tickets: TicketRow[] = [];
  subs: SubscriptionRow[] = [];

  loading = false;
  error: string | null = null;

  ordersPage = 0;
  ticketsPage = 0;
  subsPage = 0;
  pageSize = 20;
  totalOrders = 0;
  totalTickets = 0;
  totalSubs = 0;

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

  switchTab(t: 'orders' | 'tickets' | 'subs'): void {
    this.tab = t;
  }

  reset(): void {
    this.filterForm.reset({ startDate: '', endDate: '', userEmail: '', productName: '', eventId: '' });
    this.ordersPage = 0;
    this.ticketsPage = 0;
    this.subsPage = 0;
    this.loadAll();
  }

  apply(): void {
    this.ordersPage = 0;
    this.ticketsPage = 0;
    this.subsPage = 0;
    this.loadAll();
  }

  loadAll(): void {
    this.loadOrders();
    this.loadTickets();
    this.loadSubs();
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
      `${environment.apiBaseUrl}/api/shop/orders/filter`,
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
      `${environment.apiBaseUrl}/api/ticket/tickets/admin/filter`,
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
      `${environment.apiBaseUrl}/api/auth/subscriptions/admin/filter`,
      { params }
    ).subscribe({
      next: r => {
        this.subs = r.content;
        this.totalSubs = r.totalElements;
      },
      error: e => console.error('Subs', e)
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

  badgeClass(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PAID' || s === 'ACTIVE' || s === 'CONFIRMED' || s === 'DELIVERED') return 'badge badge-paid';
    if (s === 'PENDING' || s === 'PROCESSING') return 'badge badge-pending';
    if (s === 'CANCELLED' || s === 'REFUNDED' || s === 'EXPIRED') return 'badge badge-cancelled';
    if (s === 'USED') return 'badge badge-active';
    return 'badge badge-cancelled';
  }
}
