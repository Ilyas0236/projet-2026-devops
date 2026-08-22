import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

@Component({
  selector: 'app-billetterie',
  standalone: true,
  imports: [CommonModule, ErrorBannerComponent, RouterModule],
  templateUrl: './billetterie.component.html',
  styleUrls: ['./billetterie.component.scss']
})
export class BilletterieComponent implements OnInit {
  events: any[] = [];
  loading = true;
  loadError = false;
  api = inject(ApiService);

  retry() {
    this.loadError = false;
    this.ngOnInit();
  }

  ngOnInit() {
    this.api.getEvents().subscribe({
      next: (data) => {
        // Filter out completed matches
        this.events = data.filter(e => e.status !== 'COMPLETED' && e.status !== 'CANCELLED');
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  isUpcoming(status: string) {
    return status === 'UPCOMING';
  }
}
