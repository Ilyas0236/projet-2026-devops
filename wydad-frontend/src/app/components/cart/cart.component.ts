import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ErrorBannerComponent],
  template: `
    <div class="page-header">
      <h1>🛒 Mon Panier</h1>
      <p>Vos articles sélectionnés dans le Wydad Store officiel</p>
    </div>

    <div class="container">
      <!-- Loading -->
      <app-error-banner *ngIf="loadError && !loading" message="Impossible de charger le panier."
                        detail="Verifiez votre connexion et reessayez." (retry)="loadCart()" />

      <div *ngIf="loading" class="text-center py-20 text-gray-500">Chargement du panier...</div>

      <!-- Empty cart -->
      <div *ngIf="!loading && items.length === 0" class="empty-cart">
        <div class="text-6xl mb-4">🛒</div>
        <h2>Votre panier est vide</h2>
        <p>Explorez notre boutique pour trouver les meilleurs produits du Wydad AC.</p>
        <button routerLink="/boutique" class="btn-primary">Explorer la boutique</button>
      </div>

      <!-- Cart with items -->
      <div *ngIf="!loading && items.length > 0" class="cart-layout">
        <!-- Items list -->
        <div class="cart-items">
          <div class="cart-item" *ngFor="let item of items">
            <div class="item-image">
              <img *ngIf="item.productImage" [src]="item.productImage" alt="{{ item.productName }}">
              <div *ngIf="!item.productImage" class="placeholder">{{ item.productName?.charAt(0) }}</div>
            </div>
            <div class="item-info">
              <h3>{{ item.productName }}</h3>
              <p class="variant" *ngIf="item.variantInfo">{{ item.variantInfo }}</p>
              <div class="qty-controls">
                <button (click)="updateQty(item, -1)" class="qty-btn">−</button>
                <span class="qty-value">{{ item.quantity }}</span>
                <button (click)="updateQty(item, 1)" class="qty-btn">+</button>
              </div>
            </div>
            <div class="item-actions">
              <div class="item-price">{{ (item.unitPrice || 0) * item.quantity }} DH</div>
              <button (click)="removeItem(item.id)" class="remove-btn">🗑️ Supprimer</button>
            </div>
          </div>
        </div>

        <!-- Checkout sidebar -->
        <div class="checkout-sidebar">
          <h3>Récapitulatif</h3>
          <div class="summary-row">
            <span>Sous-total ({{ items.length }} articles)</span>
            <span>{{ getSubtotal() }} DH</span>
          </div>
          <div class="summary-row">
            <span>Livraison</span>
            <span class="text-green-600">{{ shippingCost > 0 ? shippingCost + ' DH' : 'Gratuite' }}</span>
          </div>
          <div class="summary-row total">
            <span>Total</span>
            <span>{{ getSubtotal() + shippingCost }} DH</span>
          </div>

          <hr class="my-4">

          <!-- Shipping info -->
          <h4>Informations de livraison</h4>
          <div class="form-group">
            <label>Adresse de livraison</label>
            <input type="text" [(ngModel)]="shippingAddress" placeholder="123 Rue Mohammed V, Casablanca">
          </div>
          <div class="form-group">
            <label>Ville</label>
            <input type="text" [(ngModel)]="shippingCity" placeholder="Casablanca">
          </div>
          <div class="form-group">
            <label>Téléphone</label>
            <input type="text" [(ngModel)]="shippingPhone" placeholder="06 XX XX XX XX">
          </div>

          <div class="form-group">
            <label>Code promo (optionnel)</label>
            <input type="text" [(ngModel)]="promoCode" placeholder="WAC2026">
          </div>

          <button (click)="placeOrder()" 
                  [disabled]="ordering || !shippingAddress || !shippingCity || !shippingPhone"
                  class="btn-checkout">
            {{ ordering ? 'Commande en cours...' : 'Valider la commande' }}
          </button>

          <p *ngIf="orderError" class="error-msg">{{ orderError }}</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(90deg, #DC143C, #9B0000);
      color: white;
      padding: 3rem 2rem;
      text-align: center;
    }
    .page-header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; font-weight: 900; }
    .page-header p { opacity: 0.7; font-size: 1.1rem; }

    .container { max-width: 1200px; margin: 0 auto; padding: 2rem; }

    .empty-cart {
      text-align: center;
      padding: 5rem 2rem;
      background: white;
      border-radius: 16px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.04);
    }
    .empty-cart h2 { font-size: 1.8rem; color: #333; margin-bottom: 0.5rem; }
    .empty-cart p { color: #666; margin-bottom: 2rem; }
    .btn-primary {
      background: linear-gradient(90deg, #d32f2f, #b71c1c);
      color: white;
      border: none;
      padding: 1rem 2.5rem;
      border-radius: 50px;
      font-weight: bold;
      cursor: pointer;
      transition: transform 0.3s;
    }
    .btn-primary:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(211,47,47,0.3); }

    .cart-layout { display: flex; gap: 2rem; flex-wrap: wrap; }
    .cart-items { flex: 2; min-width: 300px; }
    .checkout-sidebar {
      flex: 1;
      min-width: 320px;
      background: white;
      border-radius: 16px;
      padding: 2rem;
      box-shadow: 0 4px 20px rgba(0,0,0,0.04);
      border-top: 4px solid #b71c1c;
      height: fit-content;
      position: sticky;
      top: 100px;
    }

    .cart-item {
      background: white;
      border-radius: 12px;
      padding: 1.5rem;
      margin-bottom: 1rem;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      display: flex;
      gap: 1.5rem;
      align-items: center;
    }
    .item-image { width: 90px; height: 90px; border-radius: 12px; overflow: hidden; background: #f5f5f5; flex-shrink: 0; }
    .item-image img { width: 100%; height: 100%; object-fit: cover; }
    .placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 2rem; color: #ccc; font-weight: bold; }
    .item-info { flex: 1; }
    .item-info h3 { font-weight: 700; color: #333; margin-bottom: 0.25rem; }
    .variant { font-size: 0.85rem; color: #888; margin-bottom: 0.75rem; }
    .qty-controls { display: flex; align-items: center; gap: 0.75rem; }
    .qty-btn {
      width: 32px; height: 32px; border: 2px solid #ddd; border-radius: 8px;
      background: white; font-weight: bold; font-size: 1.1rem; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: all 0.2s;
    }
    .qty-btn:hover { border-color: #b71c1c; color: #b71c1c; }
    .qty-value { font-weight: 700; font-size: 1.1rem; min-width: 20px; text-align: center; }

    .item-actions { text-align: right; }
    .item-price { font-weight: 800; font-size: 1.3rem; color: #b71c1c; margin-bottom: 0.5rem; }
    .remove-btn { background: none; border: none; color: #999; font-size: 0.85rem; cursor: pointer; }
    .remove-btn:hover { color: #c62828; }

    .checkout-sidebar h3 { font-size: 1.3rem; font-weight: 800; color: #333; margin-bottom: 1.5rem; }
    .checkout-sidebar h4 { font-size: 1rem; font-weight: 700; color: #333; margin-bottom: 1rem; margin-top: 1rem; }
    .summary-row { display: flex; justify-content: space-between; margin-bottom: 0.75rem; font-size: 0.95rem; color: #555; }
    .summary-row.total { font-weight: 900; font-size: 1.2rem; color: #333; border-top: 2px solid #eee; padding-top: 0.75rem; margin-top: 0.5rem; }

    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: 0.85rem; color: #555; font-weight: 500; margin-bottom: 0.4rem; }
    .form-group input {
      width: 100%; padding: 0.7rem; border: 2px solid #e0e0e0; border-radius: 8px;
      font-size: 0.95rem; box-sizing: border-box;
    }
    .form-group input:focus { border-color: #b71c1c; outline: none; }

    .btn-checkout {
      width: 100%; padding: 1rem; background: #b71c1c; color: white; border: none;
      border-radius: 12px; font-weight: 800; font-size: 1.05rem; cursor: pointer;
      margin-top: 1rem; transition: background 0.2s;
    }
    .btn-checkout:hover:not(:disabled) { background: #9e1c1c; }
    .btn-checkout:disabled { opacity: 0.6; cursor: not-allowed; }
    .error-msg { color: #c62828; font-size: 0.9rem; margin-top: 1rem; text-align: center; }
  `]
})
export class CartComponent implements OnInit {
  items: any[] = [];
  loading = true;
  loadError = false;
  ordering = false;
  orderError = '';

  shippingAddress = '';
  shippingCity = '';
  shippingPhone = '';
  promoCode = '';
  shippingCost = 0; // free shipping for demo

  api = inject(ApiService);
  auth = inject(AuthService);
  router = inject(Router);

  ngOnInit() {
    if (!this.auth.currentUserValue) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadCart();
  }

  loadCart() {
    this.api.getCart().subscribe({
      next: (data) => { this.items = data; this.loading = false; },
      error: () => { this.loadError = true; this.loading = false; }
    });
  }

  getSubtotal(): number {
    return this.items.reduce((sum, item) => sum + ((item.unitPrice || item.price || 0) * item.quantity), 0);
  }

  updateQty(item: any, delta: number) {
    const newQ = item.quantity + delta;
    if (newQ < 1) return;
    this.api.updateCartQuantity(item.id, newQ).subscribe({
      next: () => { item.quantity = newQ; },
      error: () => this.loadCart()
    });
  }

  removeItem(cartItemId: number) {
    this.api.removeFromCart(cartItemId).subscribe({
      next: () => { this.items = this.items.filter(i => i.id !== cartItemId); },
      error: () => this.loadCart()
    });
  }

  placeOrder() {
    if (!this.shippingAddress || !this.shippingCity || !this.shippingPhone) return;

    this.ordering = true;
    this.orderError = '';

    const order = {
      shippingAddress: this.shippingAddress,
      shippingCity: this.shippingCity,
      shippingPhone: this.shippingPhone,
      promoCode: this.promoCode || null,
      clickAndCollect: false,
      items: this.items.map(i => ({
        cartItemId: i.id,
        customization: i.customization || null
      }))
    };

    this.api.createOrder(order).subscribe({
      next: (res) => {
        this.ordering = false;
        this.router.navigate(['/profil/commandes']);
      },
      error: (err) => {
        this.ordering = false;
        this.orderError = err.error?.message || 'Erreur lors de la validation de la commande.';
      }
    });
  }
}
