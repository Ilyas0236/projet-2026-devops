import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-boutique',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-boutique.component.html'
})
export class AdminBoutiqueComponent implements OnInit {
  products: any[] = [];
  totalPages = 1;
  loading = true;
  showModal = false;
  isEdit = false;
  editingId: number | null = null;

  // B.12.d — onglets : produits / codes promo
  activeTab: 'produits' | 'promos' = 'produits';

  // B.12.d — codes promo pilotés par l'ADMIN
  promoCodes: any[] = [];
  promosLoading = false;
  showPromoModal = false;
  savingPromo = false;
  togglingPromoId: number | null = null;

  newPromo = {
    code: '',
    description: '',
    discountPercent: 10,
    maxDiscountAmount: null as number | null,
    minOrderAmount: null as number | null,
    maxUses: null as number | null,
    validFrom: '',
    validUntil: ''
  };

  newProduct = {
    name: '',
    basePrice: 0,
    stockQuantity: 0,
    sportSection: 'FOOTBALL',
    categoryName: '',
    description: '',
    mainImageUrl: ''
  };

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadProducts();
  }

  // ========== B.12.d — CODES PROMO ==========
  switchTab(tab: 'produits' | 'promos') {
    this.activeTab = tab;
    if (tab === 'promos') this.loadPromoCodes();
  }

  loadPromoCodes() {
    this.promosLoading = true;
    this.api.getPromoCodes().subscribe({
      next: (list) => {
        this.promoCodes = list || [];
        this.promosLoading = false;
      },
      error: (err) => {
        console.error('Erreur chargement codes promo', err);
        this.promosLoading = false;
        this.toast.error('Erreur lors du chargement des codes promo.');
      }
    });
  }

  openPromoModal() {
    this.newPromo = {
      code: '', description: '', discountPercent: 10,
      maxDiscountAmount: null, minOrderAmount: null, maxUses: null,
      validFrom: '', validUntil: ''
    };
    this.showPromoModal = true;
  }

  closePromoModal() {
    this.showPromoModal = false;
  }

  savePromoCode() {
    if (!this.newPromo.code.trim()) return;
    const payload: any = {
      code: this.newPromo.code.trim(),
      description: this.newPromo.description || null,
      discountPercent: this.newPromo.discountPercent
    };
    if (this.newPromo.maxDiscountAmount != null) payload.maxDiscountAmount = this.newPromo.maxDiscountAmount;
    if (this.newPromo.minOrderAmount != null) payload.minOrderAmount = this.newPromo.minOrderAmount;
    if (this.newPromo.maxUses != null) payload.maxUses = this.newPromo.maxUses;
    if (this.newPromo.validFrom) payload.validFrom = new Date(this.newPromo.validFrom).toISOString();
    if (this.newPromo.validUntil) payload.validUntil = new Date(this.newPromo.validUntil).toISOString();

    this.savingPromo = true;
    this.api.createPromoCode(payload).subscribe({
      next: () => {
        this.savingPromo = false;
        this.closePromoModal();
        this.loadPromoCodes();
      },
      error: (err) => {
        console.error('Erreur création code promo', err);
        this.savingPromo = false;
        this.toast.error(err.error?.message || 'Erreur lors de la création du code promo.');
      }
    });
  }

  async togglePromo(promo: any) {
    const target = !promo.active;
    const ok = await this.confirm.confirm({
      title: target ? 'Réactiver le code' : 'Désactiver le code',
      message: target
        ? `Le code ${promo.code} redevient utilisable par les membres.`
        : `Le code ${promo.code} ne sera plus applicable en commande.`,
      confirmLabel: target ? 'Réactiver' : 'Désactiver',
      danger: !target
    });
    if (!ok) return;
    this.togglingPromoId = promo.id;
    this.api.setPromoCodeActive(promo.id, target).subscribe({
      next: () => {
        this.togglingPromoId = null;
        this.loadPromoCodes();
      },
      error: (err) => {
        console.error('Erreur activation code promo', err);
        this.togglingPromoId = null;
        this.toast.error(err.error?.message || 'Erreur lors de la modification du code.');
      }
    });
  }

  loadProducts() {
    this.loading = true;
    this.api.getProducts().subscribe({
      next: (res) => {
        // Le backend renvoie une Page Spring : { content: [...], totalPages: n }
        const page: any = res;
        this.products = page?.content || [];
        this.totalPages = page?.totalPages || 1;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement produits', err);
        this.loading = false;
      }
    });
  }

  openAddModal() {
    this.isEdit = false;
    this.editingId = null;
    this.newProduct = { name: '', basePrice: 0, stockQuantity: 0, sportSection: 'FOOTBALL', categoryName: '', description: '', mainImageUrl: '' };
    this.showModal = true;
  }

  openEditModal(product: any) {
    this.isEdit = true;
    this.editingId = product.id;
    const stock = product.variants?.[0]?.stockQuantity ?? 0;
    this.newProduct = {
      name: product.name,
      basePrice: product.basePrice,
      stockQuantity: stock,
      sportSection: product.sportSection || 'FOOTBALL',
      categoryName: product.categoryName || '',
      description: product.description || '',
      mainImageUrl: product.images?.[0] || ''
    };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  saveProduct() {
    const payload: any = { ...this.newProduct };
    if (!payload.categoryName) delete payload.categoryName;

    const call$ = this.isEdit && this.editingId
      ? this.api.updateProduct(this.editingId, payload)
      : this.api.createProduct(payload);

    call$.subscribe({
      next: () => {
        this.loadProducts();
        this.closeAddModal();
      },
      error: (err) => {
        console.error('Erreur sauvegarde produit', err);
        this.toast.error(err.error?.message || 'Erreur lors de la sauvegarde du produit.');
      }
    });
  }

  async deleteProduct(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le produit',
      message: 'Êtes-vous sûr de vouloir supprimer ce produit ? Cette action est irréversible.',
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteProduct(id).subscribe({
      next: () => this.loadProducts(),
      error: (err) => console.error('Erreur suppression', err)
    });
  }

  uploadingPhoto = false;

  uploadPhoto(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    this.uploadingPhoto = true;
    this.api.uploadMedia(file).subscribe({
      next: (res) => {
        this.newProduct.mainImageUrl = res.url;
        this.uploadingPhoto = false;
      },
      error: (err) => {
        console.error('Erreur upload', err);
        this.uploadingPhoto = false;
        this.toast.error('Erreur lors du chargement de la photo.');
      }
    });
  }
}
