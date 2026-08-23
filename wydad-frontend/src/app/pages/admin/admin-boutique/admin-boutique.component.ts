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
