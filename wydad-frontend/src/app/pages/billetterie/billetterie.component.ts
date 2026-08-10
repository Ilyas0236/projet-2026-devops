import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-billetterie',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './billetterie.component.html',
  styleUrls: ['./billetterie.component.scss']
})
export class BilletterieComponent implements OnInit {
  events: any[] = [];
  loading = true;
  api = inject(ApiService);

  ngOnInit() {
    this.api.getEvents().subscribe({
      next: (data) => {
        // Filter out completed matches
        this.events = data.filter(e => e.status !== 'COMPLETED' && e.status !== 'CANCELLED');
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.events = [];
      }
    });
  }

  isUpcoming(status: string) {
    return status === 'UPCOMING';
  }
}
