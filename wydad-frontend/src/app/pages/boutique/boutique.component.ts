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

