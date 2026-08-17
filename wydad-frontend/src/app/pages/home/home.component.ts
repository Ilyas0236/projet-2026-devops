import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  nextMatch: any = null;
  articles: any[] = [];
  api = inject(ApiService);

  ngOnInit() {
    // Fetch upcoming match
    this.api.getEvents().subscribe({
      next: (events) => {
        const upcoming = events.filter(e => e.status === 'UPCOMING');
        if (upcoming.length > 0) {
          // Sort by date (assuming closest first)
          upcoming.sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime());
          this.nextMatch = upcoming[0];
        }
      },
      error: () => {
        this.nextMatch = null;
      }
    });

    // Fetch news
    this.api.getArticles().subscribe({
      next: (data) => {
        this.articles = data.slice(0, 2); // Get top 2 news
      },
      error: () => {
        this.articles = [];
      }
    });
  }
}
