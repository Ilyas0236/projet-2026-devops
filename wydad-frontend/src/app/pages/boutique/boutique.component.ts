import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-boutique',
  standalone: true,
  imports: [CommonModule, ErrorBannerComponent, RouterModule],
  templateUrl: './boutique.component.html',
  styleUrls: ['./boutique.component.scss']
})
export class BoutiqueComponent implements OnInit {
  products: any[] = [];
  loading = true;
  loadError = false;
  api = inject(ApiService);

  /** Filtre actif : 'ALL' ou une catégorie dérivée des produits chargés. */
  activeFilter = 'ALL';

  /** Catégories réellement présentes dans le catalogue (aucune donnée hardcodée). */
  get filters(): string[] {
    const names = new Set<string>();
    for (const p of this.products) {
      const name = p.categoryName || p.sportSection;
      if (name) names.add(name);
    }
    return Array.from(names).sort();
  }

  get filteredProducts(): any[] {
    if (this.activeFilter === 'ALL') return this.products;
    return this.products.filter(p =>
      (p.categoryName || p.sportSection) === this.activeFilter);
  }

  setFilter(filter: string) {
    this.activeFilter = filter;
  }

  retry() {
    this.loadError = false;
    this.ngOnInit();
  }

  ngOnInit() {
    this.api.getProducts().subscribe({
      next: (data: any) => {
        // Handle paginated response (Spring Page<ProductDto>)
        this.products = data?.content || data || [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }
}

