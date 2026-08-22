import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-boutique-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent],
  template: `
    <div class="min-h-screen bg-wydad-light pt-32 pb-24 font-sans">
      <div class="max-w-7xl mx-auto px-6" *ngIf="loading">
        <div class="text-center py-20 text-gray-500">Chargement du produit...</div>
      </div>

      <div class="max-w-7xl mx-auto px-6" *ngIf="!loading && product">
        <div class="flex flex-col lg:flex-row gap-12">

          <!-- LEFT: Image Gallery -->
          <div class="lg:w-1/2">
            <div class="bg-white rounded-2xl overflow-hidden shadow-lg border border-gray-100">
              <div class="aspect-square bg-gray-50 flex items-center justify-center p-8 relative">
                <img *ngIf="mainImage" [src]="mainImage" alt="{{ product.name }}" class="max-w-full max-h-full object-contain">
                <span *ngIf="!mainImage" class="text-gray-300 font-display text-4xl font-bold uppercase">{{ product.name }}</span>
              </div>
            </div>
            <div *ngIf="product.images && product.images.length > 1" class="flex gap-3 mt-4">
              <div *ngFor="let img of product.images; let i = index"
                   (click)="mainImage = img"
                   [class.ring-2]="mainImage === img"
                   [class.ring-wydad-red]="mainImage === img"
                   class="w-20 h-20 bg-white rounded-lg overflow-hidden border border-gray-200 cursor-pointer hover:border-wydad-red transition-colors">
                <img [src]="img" class="w-full h-full object-cover">
              </div>
            </div>
          </div>

          <!-- RIGHT: Product Info -->
          <div class="lg:w-1/2">
            <div class="mb-2">
              <span class="text-wydad-red text-xs font-bold uppercase tracking-widest">{{ product.sportSection }} · {{ product.categoryName }}</span>
            </div>
            <h1 class="font-display font-black text-4xl text-wydad-dark uppercase tracking-tight mb-4">{{ product.name }}</h1>

            <!-- Rating -->
            <div *ngIf="product.averageRating" class="flex items-center gap-2 mb-4">
              <span class="text-yellow-500 text-lg">{{ getStars(product.averageRating) }}</span>
              <span class="text-sm text-gray-500">({{ product.reviewCount }} avis)</span>
            </div>

            <!-- Price -->
            <div class="text-3xl font-black text-wydad-red mb-6">{{ product.basePrice }} DH</div>

            <!-- Description -->
            <p class="text-gray-600 leading-relaxed mb-8 text-sm">{{ product.description }}</p>

            <!-- Size selector -->
            <div *ngIf="availableSizes.length > 0" class="mb-6">
              <h3 class="text-sm font-bold uppercase tracking-wider text-gray-800 mb-3">Taille</h3>
              <div class="flex flex-wrap gap-2">
                <button *ngFor="let size of availableSizes"
                        (click)="selectSize(size)"
                        [class.bg-wydad-dark]="selectedSize === size"
                        [class.text-white]="selectedSize === size"
                        [class.border-wydad-dark]="selectedSize === size"
                        class="min-w-[48px] h-12 border-2 border-gray-300 rounded-lg font-bold text-sm uppercase hover:border-wydad-dark transition-colors flex items-center justify-center">
                  {{ size }}
                </button>
              </div>
            </div>

            <!-- Color selector -->
            <div *ngIf="availableColors.length > 0" class="mb-6">
              <h3 class="text-sm font-bold uppercase tracking-wider text-gray-800 mb-3">Couleur</h3>
              <div class="flex gap-3">
                <button *ngFor="let color of availableColors"
                        (click)="selectColor(color)"
                        [class.ring-2]="selectedColor === color.color"
                        [class.ring-wydad-red]="selectedColor === color.color"
                        [style.background-color]="color.hex"
                        class="w-10 h-10 rounded-full border-2 border-gray-200 ring-offset-2 transition-all"
                        [title]="color.color">
                </button>
              </div>
            </div>

            <!-- Stock indicator -->
            <div *ngIf="selectedVariant" class="mb-6">
              <span *ngIf="selectedVariant.stockQuantity > 5" class="text-green-600 text-sm font-bold flex items-center gap-1">
                <span class="w-2 h-2 bg-green-500 rounded-full"></span> En stock ({{ selectedVariant.stockQuantity }})
              </span>
              <span *ngIf="selectedVariant.stockQuantity > 0 && selectedVariant.stockQuantity <= 5" class="text-orange-500 text-sm font-bold flex items-center gap-1">
                <span class="w-2 h-2 bg-orange-400 rounded-full"></span> Plus que {{ selectedVariant.stockQuantity }} restant(s) !
              </span>
              <span *ngIf="selectedVariant.stockQuantity <= 0" class="text-red-600 text-sm font-bold flex items-center gap-1">
                <span class="w-2 h-2 bg-red-500 rounded-full"></span> Rupture de stock
              </span>
            </div>

            <!-- Quantity -->
            <div class="mb-8">
              <h3 class="text-sm font-bold uppercase tracking-wider text-gray-800 mb-3">Quantité</h3>
              <div class="flex items-center gap-4 bg-gray-100 p-2 rounded-lg w-fit">
                <button (click)="changeQty(-1)" class="w-10 h-10 flex items-center justify-center bg-white hover:bg-wydad-red hover:text-white rounded-lg text-wydad-dark font-bold transition-colors shadow-sm">-</button>
                <span class="font-bold text-lg w-6 text-center">{{ quantity }}</span>
                <button (click)="changeQty(1)" class="w-10 h-10 flex items-center justify-center bg-white hover:bg-wydad-red hover:text-white rounded-lg text-wydad-dark font-bold transition-colors shadow-sm">+</button>
              </div>
            </div>

            <!-- Add to cart -->
            <button (click)="addToCart()"
                    [disabled]="!selectedVariant || selectedVariant.stockQuantity <= 0 || addingToCart"
                    class="w-full py-4 bg-wydad-dark hover:bg-wydad-red text-white font-display uppercase font-bold tracking-wider rounded-xl transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-3 shadow-lg">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/></svg>
              {{ addingToCart ? 'Ajout en cours...' : 'Ajouter au panier' }}
            </button>

            <!-- Success message -->
            <div *ngIf="cartSuccess" class="mt-4 bg-green-50 border border-green-200 text-green-800 rounded-lg p-4 text-sm font-medium flex items-center gap-2">
              ✅ Produit ajouté au panier !
              <button (click)="router.navigate(['/panier'])" class="ml-auto text-green-700 font-bold hover:underline">Voir le panier →</button>
            </div>
            <div *ngIf="cartError" class="mt-4 bg-red-50 border border-red-200 text-red-800 rounded-lg p-4 text-sm font-medium">
              ❌ {{ cartError }}
            </div>

            <!-- SKU -->
            <div class="mt-8 pt-6 border-t border-gray-200">
              <p class="text-xs text-gray-400">SKU : {{ product.sku }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="max-w-7xl mx-auto px-6" *ngIf="loadError && !loading">
        <app-error-banner message="Impossible de charger ce produit."
                          detail="Il est peut-être indisponible ou la connexion a échoué." (retry)="retry()" />
      </div>

      <div class="max-w-7xl mx-auto px-6 text-center py-20" *ngIf="!loading && !product && !loadError">
        <h2 class="text-2xl font-bold text-gray-700">Produit introuvable</h2>
        <button (click)="router.navigate(['/boutique'])" class="mt-6 bg-wydad-red text-white font-bold py-3 px-8 rounded-full">Retour à la boutique</button>
      </div>
    </div>
  `
})
export class BoutiqueDetailComponent implements OnInit {
  product: any = null;
  loading = true;
  loadError = false;
  mainImage: string | null = null;

  availableSizes: string[] = [];
  availableColors: { color: string; hex: string }[] = [];
  selectedSize: string | null = null;
  selectedColor: string | null = null;
  selectedVariant: any = null;
  quantity = 1;

  addingToCart = false;
  cartSuccess = false;
  cartError = '';

  api = inject(ApiService);
  auth = inject(AuthService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.api.getProductById(Number(id)).subscribe({
        next: (data) => {
          this.product = data;
          this.mainImage = data.images?.[0] || null;
          this.extractVariants(data.variants || []);
          this.loading = false;
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
        }
      });
    }
  }

  retry() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadError = false;
      this.loading = true;
      this.api.getProductById(Number(id)).subscribe({
        next: (data) => {
          this.product = data;
          this.mainImage = data.images?.[0] || null;
          this.extractVariants(data.variants || []);
          this.loading = false;
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
        }
      });
    }
  }

  extractVariants(variants: any[]) {
    const sizes = new Set<string>();
    const colorsMap = new Map<string, string>();
    for (const v of variants) {
      if (v.size) sizes.add(v.size);
      if (v.color && v.colorHex) colorsMap.set(v.color, v.colorHex);
    }
    this.availableSizes = Array.from(sizes);
    this.availableColors = Array.from(colorsMap).map(([color, hex]) => ({ color, hex }));
    // Auto-select first
    if (this.availableSizes.length > 0) this.selectedSize = this.availableSizes[0];
    if (this.availableColors.length > 0) this.selectedColor = this.availableColors[0].color;
    this.resolveVariant();
  }

  selectSize(size: string) {
    this.selectedSize = size;
    this.resolveVariant();
  }

  selectColor(color: { color: string; hex: string }) {
    this.selectedColor = color.color;
    this.resolveVariant();
  }

  resolveVariant() {
    if (!this.product?.variants) return;
    this.selectedVariant = this.product.variants.find((v: any) =>
      (!this.selectedSize || v.size === this.selectedSize) &&
      (!this.selectedColor || v.color === this.selectedColor)
    ) || this.product.variants[0] || null;
  }

  changeQty(delta: number) {
    const newQ = this.quantity + delta;
    const max = this.selectedVariant?.stockQuantity || 10;
    if (newQ >= 1 && newQ <= max) this.quantity = newQ;
  }

  getStars(rating: number): string {
    return '★'.repeat(Math.round(rating)) + '☆'.repeat(5 - Math.round(rating));
  }

  addToCart() {
    if (!this.selectedVariant || !this.auth.currentUserValue) {
      this.cartError = 'Veuillez vous connecter pour ajouter au panier.';
      return;
    }
    this.addingToCart = true;
    this.cartSuccess = false;
    this.cartError = '';

    const item = {
      productVariantId: this.selectedVariant.id,
      productId: this.product.id,
      productName: this.product.name,
      productImage: this.product.images?.[0] || '',
      variantInfo: `${this.selectedSize || ''} ${this.selectedColor || ''}`.trim(),
      quantity: this.quantity
    };

    this.api.addToCart(item).subscribe({
      next: () => {
        this.addingToCart = false;
        this.cartSuccess = true;
      },
      error: (err) => {
        this.addingToCart = false;
        this.cartError = err.error?.message || 'Erreur lors de l\'ajout au panier.';
      }
    });
  }
}
