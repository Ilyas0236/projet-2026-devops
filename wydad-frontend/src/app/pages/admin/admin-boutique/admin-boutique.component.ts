import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-admin-boutique',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-boutique.component.html'
})
export class AdminBoutiqueComponent implements OnInit {
  products: any[] = [];
  loading = true;
  showModal = false;
  
  newProduct = {
    name: '',
    price: 0,
    stock: 0,
    category: 'MAILLOT',
    description: 'Produit officiel'
  };

  api = inject(ApiService);

  ngOnInit() {
    this.loadProducts();
  }

  loadProducts() {
    this.loading = true;
    this.api.getProducts().subscribe({
      next: (data) => {
        this.products = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement produits', err);
        this.loading = false;
      }
    });
  }

  openAddModal() {
    this.newProduct = { name: '', price: 0, stock: 0, category: 'MAILLOT', description: 'Produit officiel' };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  saveProduct() {
    this.api.createProduct(this.newProduct).subscribe({
      next: (res) => {
        this.loadProducts();
        this.closeAddModal();
      },
      error: (err) => {
        console.error('Erreur création produit', err);
        alert('Erreur lors de la création du produit');
      }
    });
  }

  deleteProduct(id: number) {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce produit ?')) {
      this.api.deleteProduct(id).subscribe({
        next: () => this.loadProducts(),
        error: (err) => console.error('Erreur suppression', err)
      });
    }
  }
}
