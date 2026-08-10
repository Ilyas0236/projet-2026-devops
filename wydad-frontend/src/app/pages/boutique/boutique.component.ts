import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-boutique',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './boutique.component.html',
  styleUrls: ['./boutique.component.scss']
})
export class BoutiqueComponent implements OnInit {
  products: any[] = [];
  loading = true;
  api = inject(ApiService);

  ngOnInit() {
    this.api.getProducts().subscribe({
      next: (data: any) => {
        // Handle paginated response (Spring Page<ProductDto>)
        this.products = data?.content || data || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.products = [];
      }
    });
  }
}

