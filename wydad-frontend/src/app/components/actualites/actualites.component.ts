import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-actualites',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-header">
      <h1>📰 Actualités Wydad AC</h1>
      <p>
        Toute l'actualité du club : football, basket, handball, volley et plus
      </p>
    </div>

    <div class="container">
      <div class="filters">
        <button (click)="filter = 'ALL'" [class.active]="filter === 'ALL'">
          Tous
        </button>
        <button
          (click)="filter = 'FOOTBALL'"
          [class.active]="filter === 'FOOTBALL'"
        >
          ⚽ Football
        </button>
        <button
          (click)="filter = 'BASKET'"
          [class.active]="filter === 'BASKET'"
        >
          🏀 Basket
        </button>
        <button (click)="filter = 'HAND'" [class.active]="filter === 'HAND'">
          🤾 Hand
        </button>
        <button
          (click)="filter = 'VOLLEY'"
          [class.active]="filter === 'VOLLEY'"
        >
          🏐 Volley
        </button>
      </div>

      <div class="news-grid">
        <div class="news-card" *ngFor="let article of filteredArticles()">
          <div
            class="news-image"
            [style.backgroundImage]="
              'url(' +
              (article.imageUrl ||
                'https://via.placeholder.com/600x350/d32f2f/ffffff?text=WYDAD') +
              ')'
            "
          ></div>
          <div class="news-body">
            <div class="meta">
              <span class="sport-tag">{{ article.sport }}</span>
              <span class="date">{{
                article.createdAt | date: 'dd MMM yyyy'
              }}</span>
            </div>
            <h2>{{ article.titre }}</h2>
            <p>{{ article.contenu }}</p>
            <div class="author">✍️ Par {{ article.auteur }}</div>
          </div>
        </div>
      </div>

      <div *ngIf="filteredArticles().length === 0" class="empty">
        <p>Aucune actualité dans cette section.</p>
      </div>
    </div>
  `,
  styles: [
    `
      .page-header {
        background: linear-gradient(90deg, #b71c1c, #8e0000);
        color: white;
        padding: 3rem 2rem;
        text-align: center;
      }
      .page-header h1 {
        font-size: 2.5rem;
        margin-bottom: 0.5rem;
      }
      .page-header p {
        opacity: 0.9;
        font-size: 1.1rem;
      }

      .container {
        max-width: 1200px;
        margin: 0 auto;
        padding: 2rem;
      }

      .filters {
        display: flex;
        gap: 0.75rem;
        margin-bottom: 2rem;
        flex-wrap: wrap;
        justify-content: center;
      }
      .filters button {
        padding: 0.5rem 1.25rem;
        border: 2px solid #ddd;
        background: white;
        border-radius: 50px;
        cursor: pointer;
        font-weight: 500;
        transition: all 0.2s;
      }
      .filters button:hover,
      .filters button.active {
        background: #b71c1c;
        color: white;
        border-color: #b71c1c;
      }

      .news-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
        gap: 2rem;
      }
      .news-card {
        background: white;
        border-radius: 16px;
        overflow: hidden;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
        transition: transform 0.3s;
      }
      .news-card:hover {
        transform: translateY(-5px);
        box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
      }
      .news-image {
        height: 220px;
        background-size: cover;
        background-position: center;
      }
      .news-body {
        padding: 1.5rem;
      }
      .meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.75rem;
      }
      .sport-tag {
        background: #d32f2f;
        color: white;
        padding: 0.25rem 0.75rem;
        border-radius: 20px;
        font-size: 0.75rem;
        font-weight: bold;
      }
      .date {
        color: #999;
        font-size: 0.85rem;
      }
      .news-body h2 {
        color: #333;
        font-size: 1.3rem;
        margin-bottom: 0.75rem;
      }
      .news-body p {
        color: #666;
        line-height: 1.6;
        margin-bottom: 1rem;
      }
      .author {
        color: #b71c1c;
        font-weight: 500;
        font-size: 0.9rem;
      }

      .empty {
        text-align: center;
        padding: 4rem;
        color: #999;
      }
    `,
  ],
})
export class ActualitesComponent implements OnInit {
  articles: any[] = [];
  filter = 'ALL';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getArticles().subscribe({
      next: (data) => (this.articles = data),
      error: () => (this.articles = []),
    });
  }

  filteredArticles() {
    if (this.filter === 'ALL') return this.articles;
    return this.articles.filter((a) => a.sport === this.filter);
  }
}
