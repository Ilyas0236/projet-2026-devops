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
        // Fallback for demo
        this.nextMatch = {
          homeTeam: 'Wydad AC',
          awayTeam: 'Raja CA',
          competition: 'Botola Pro - J12',
          eventDate: '2026-08-25T20:00:00',
          venue: 'Stade Mohammed V, Casablanca'
        };
      }
    });

    // Fetch news
    this.api.getArticles().subscribe({
      next: (data) => {
        this.articles = data.slice(0, 2); // Get top 2 news
      },
      error: () => {
        // Fallback for demo
        this.articles = [
          {
            title: "Préparation intensive avant le Derby de Casablanca",
            category: "Équipe Première",
            excerpt: "L'équipe s'entraîne d'arrache-pied au complexe Mohamed Benjelloun.",
            imageUrl: "https://images.unsplash.com/photo-1518605368461-1e1e34cad454?ixlib=rb-4.0.3&auto=format&fit=crop&w=1000&q=80"
          },
          {
            title: "Les U19 remportent le tournoi international",
            category: "Académie",
            imageUrl: "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"
          }
        ];
      }
    });
  }
}
