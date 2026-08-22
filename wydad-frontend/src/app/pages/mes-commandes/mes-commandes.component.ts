import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-mes-commandes',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  template: `
    <div class="page-header">
      <h1>🛍️ Mes Commandes</h1>
      <p>Suivez vos achats et l'état de vos livraisons</p>
    </div>

    <div class="container mx-auto max-w-7xl px-4 py-8" *ngIf="isLoggedIn; else notConnected">
      <div class="flex flex-col md:flex-row gap-8">

        <!-- Sidebar -->
        <div class="w-full md:w-1/4">
          <div class="sidebar">
            <h3>Mon Espace</h3>
            <a routerLink="/profil">⚙️ Paramètres du compte</a>
            <a routerLink="/profil/carte">🎟️ Ma Carte Membre</a>
            <a routerLink="/profil/ecash">💰 Porte-Monnaie E-Cash</a>
            <a routerLink="/profil/billets">🎫 Mes Billets</a>
            <a routerLink="/profil/commandes" class="active">🛍️ Mes Commandes</a>
          </div>
        </div>

        <!-- Orders List -->
        <div class="w-full md:w-3/4">
          <app-error-banner *ngIf="loadError && !loading" message="Impossible de charger vos commandes."
                            detail="Vérifiez votre connexion et réessayez." (retry)="retry()" />

          <div *ngIf="loading" class="text-center py-10 text-gray-500">Chargement de vos commandes...</div>

          <div *ngIf="!loading && orders.length === 0" class="empty-state">
            <div class="text-6xl mb-4">📦</div>
            <h2>Aucune commande</h2>
            <p>Vous n'avez pas encore passé de commande dans notre boutique.</p>
            <button routerLink="/boutique" class="btn-primary">Découvrir la boutique</button>
          </div>

          <div *ngIf="!loading && orders.length > 0" class="orders-list">
            <div *ngFor="let order of orders" class="order-card">
              <!-- Order Header -->
              <div class="order-header" (click)="toggleOrder(order.orderNumber)">
                <div class="order-meta">
                  <span class="order-number">N° {{ order.orderNumber }}</span>
                  <span class="order-date">{{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                </div>
                <div class="order-status-price">
                  <span class="status-badge" [ngClass]="getStatusClass(order.status)">{{ getStatusLabel(order.status) }}</span>
                  <span class="order-total">{{ order.totalAmount }} DH</span>
                </div>
                <span class="expand-icon">{{ expandedOrder === order.orderNumber ? '▲' : '▼' }}</span>
              </div>

              <!-- Order Details (expanded) -->
              <div *ngIf="expandedOrder === order.orderNumber" class="order-details">
                <div class="order-items">
                  <div *ngFor="let item of order.items" class="order-item">
                    <div class="item-image-sm">
                      <img *ngIf="item.productImage" [src]="item.productImage">
                      <div *ngIf="!item.productImage" class="placeholder-sm">📦</div>
                    </div>
                    <div class="item-info">
                      <strong>{{ item.productName }}</strong>
                      <span class="text-gray-500 text-sm" *ngIf="item.variantInfo">{{ item.variantInfo }}</span>
                      <span class="text-gray-400 text-xs">Qté: {{ item.quantity }} × {{ item.unitPrice }} DH</span>
                    </div>
                    <div class="item-total">{{ item.totalPrice }} DH</div>
                  </div>
                </div>

                <div class="order-summary">
                  <div class="summary-line">
                    <span>Sous-total</span>
                    <span>{{ order.subtotal }} DH</span>
                  </div>
                  <div class="summary-line" *ngIf="order.shippingCost > 0">
                    <span>Livraison</span>
                    <span>{{ order.shippingCost }} DH</span>
                  </div>
                  <div class="summary-line" *ngIf="order.discountAmount > 0">
                    <span>Réduction</span>
                    <span class="text-green-600">-{{ order.discountAmount }} DH</span>
                  </div>
                  <div class="summary-line total-line">
                    <span>Total payé</span>
                    <span>{{ order.totalAmount }} DH</span>
                  </div>
                  <div class="tracking" *ngIf="order.trackingNumber">
                    📦 Suivi : <code>{{ order.trackingNumber }}</code>
                  </div>
                </div>
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
        <p class="text-gray-500 mb-6">Veuillez vous connecter pour voir vos commandes.</p>
        <button routerLink="/login" class="btn-primary">Se connecter</button>
      </div>
    </ng-template>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(90deg, #b71c1c, #8e0000);
      color: white; padding: 3rem 2rem; text-align: center;
    }
    .page-header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; font-weight: 900; }
    .page-header p { opacity: 0.9; font-size: 1.1rem; }

    .sidebar {
      background: white; border-radius: 12px; padding: 1.5rem;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04); border-top: 4px solid #b71c1c;
    }
    .sidebar h3 { font-weight: 700; color: #333; margin-bottom: 1rem; font-size: 1.1rem; }
    .sidebar a {
      display: block; padding: 0.6rem 0.75rem; border-radius: 8px;
      color: #555; text-decoration: none; font-weight: 500; font-size: 0.95rem;
      margin-bottom: 0.25rem; transition: all 0.2s;
    }
    .sidebar a:hover { background: #f5f5f5; color: #333; }
    .sidebar a.active { background: #fef2f2; color: #b71c1c; font-weight: 700; }

    .empty-state {
      text-align: center; padding: 5rem 2rem;
      background: white; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.04);
    }
    .empty-state h2 { font-size: 1.8rem; color: #333; margin-bottom: 0.5rem; }
    .empty-state p { color: #666; margin-bottom: 2rem; }
    .btn-primary {
      background: linear-gradient(90deg, #d32f2f, #b71c1c); color: white;
      border: none; padding: 0.9rem 2rem; border-radius: 50px; font-weight: bold;
      cursor: pointer; transition: transform 0.3s; text-decoration: none; display: inline-block;
    }
    .btn-primary:hover { transform: translateY(-2px); }

    .orders-list { display: flex; flex-direction: column; gap: 1rem; }

    .order-card {
      background: white; border-radius: 12px; overflow: hidden;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04); border: 1px solid #f0f0f0;
    }
    .order-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 1.25rem 1.5rem; cursor: pointer; transition: background 0.2s;
      gap: 1rem; flex-wrap: wrap;
    }
    .order-header:hover { background: #fafafa; }
    .order-meta { display: flex; flex-direction: column; gap: 0.25rem; }
    .order-number { font-weight: 800; color: #333; font-size: 1rem; }
    .order-date { font-size: 0.8rem; color: #999; }
    .order-status-price { display: flex; align-items: center; gap: 1rem; }
    .order-total { font-weight: 800; font-size: 1.2rem; color: #333; }
    .expand-icon { color: #999; font-size: 0.8rem; }

    .status-badge {
      padding: 0.3rem 0.8rem; border-radius: 50px; font-size: 0.75rem;
      font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;
    }
    .status-pending { background: #fff3e0; color: #e65100; }
    .status-confirmed { background: #e3f2fd; color: #1565c0; }
    .status-shipped { background: #f3e5f5; color: #7b1fa2; }
    .status-delivered { background: #e8f5e9; color: #2e7d32; }
    .status-cancelled { background: #ffebee; color: #c62828; }

    .order-details { border-top: 1px solid #f0f0f0; padding: 1.5rem; background: #fafafa; }
    .order-items { margin-bottom: 1.5rem; }
    .order-item {
      display: flex; align-items: center; gap: 1rem;
      padding: 0.75rem 0; border-bottom: 1px solid #eee;
    }
    .order-item:last-child { border-bottom: none; }
    .item-image-sm { width: 50px; height: 50px; border-radius: 8px; overflow: hidden; background: #eee; flex-shrink: 0; }
    .item-image-sm img { width: 100%; height: 100%; object-fit: cover; }
    .placeholder-sm { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 1.5rem; }
    .item-info { flex: 1; display: flex; flex-direction: column; gap: 0.15rem; }
    .item-info strong { color: #333; font-size: 0.95rem; }
    .item-total { font-weight: 700; color: #333; }

    .order-summary {
      background: white; border-radius: 10px; padding: 1rem 1.25rem;
      border: 1px solid #eee;
    }
    .summary-line { display: flex; justify-content: space-between; padding: 0.4rem 0; font-size: 0.9rem; color: #555; }
    .total-line { font-weight: 800; font-size: 1.1rem; color: #333; border-top: 2px solid #eee; padding-top: 0.75rem; margin-top: 0.5rem; }
    .tracking { margin-top: 1rem; font-size: 0.9rem; color: #555; }
    .tracking code { background: #f5f5f5; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 600; }
  `]
})
export class MesCommandesComponent implements OnInit {
  orders: any[] = [];
  loading = true;
  loadError = false;
  isLoggedIn = false;
  expandedOrder: string | null = null;

  api = inject(ApiService);
  auth = inject(AuthService);

  ngOnInit() {
    this.auth.currentUser$.subscribe(email => {
      this.isLoggedIn = !!email;
      if (email) this.loadOrders();
    });
  }

  loadOrders() {
    this.api.getMyOrders().subscribe({
      next: (data) => {
        // data could be paginated { content: [] } or just an array
        this.orders = data?.content || data || [];
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
    this.ngOnInit();
  }

  toggleOrder(orderNumber: string) {
    this.expandedOrder = this.expandedOrder === orderNumber ? null : orderNumber;
  }

  getStatusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PENDING': return 'status-pending';
      case 'CONFIRMED': return 'status-confirmed';
      case 'SHIPPED': return 'status-shipped';
      case 'DELIVERED': return 'status-delivered';
      case 'CANCELLED': return 'status-cancelled';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PENDING': return 'En attente';
      case 'CONFIRMED': return 'Confirmée';
      case 'SHIPPED': return 'Expédiée';
      case 'DELIVERED': return 'Livrée';
      case 'CANCELLED': return 'Annulée';
      default: return status;
    }
  }
}
