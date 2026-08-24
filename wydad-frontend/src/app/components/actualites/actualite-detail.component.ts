import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-actualite-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="container" *ngIf="article; else loadingTpl">
      <!-- BREADCRUMBS -->
      <div class="breadcrumbs">
        <a routerLink="/actualites">📰 Actualités</a>
        <span>/</span>
        <span>{{ article.titre }}</span>
      </div>

      <!-- COVER IMAGE -->
      <div class="cover-image" [style.backgroundImage]="'url(' + (article.imageUrl || 'assets/images/defaults/default-news-2.svg') + ')'"></div>

      <!-- ARTICLE BODY -->
      <div class="article-card">
        <div class="meta-row">
          <span class="sport-tag">{{ article.sport }}</span>
          <span class="date">📅 {{ article.createdAt | date:'dd MMMM yyyy' }}</span>
        </div>

        <h1>{{ article.titre }}</h1>
        <div class="author">✍️ Par <strong>{{ article.auteur }}</strong></div>

        <hr>

        <div class="content">
          <p class="lead-text">{{ getLeadText() }}</p>
          <p class="body-text">{{ getBodyText() }}</p>
        </div>

        <div class="footer-actions">
          <a routerLink="/actualites" class="btn-back">⬅️ Retour aux actualités</a>
        </div>
      </div>
    </div>

    <ng-template #loadingTpl>
      <div class="container loading-container">
        <div class="spinner">⏳</div>
        <p>Chargement de l'actualité...</p>
      </div>
    </ng-template>
  `,
  styles: [`
    .container { max-width: 900px; margin: 2rem auto; padding: 0 2rem; }
    
    .breadcrumbs {
      display: flex;
      gap: 0.5rem;
      font-size: 0.9rem;
      color: #777;
      margin-bottom: 1.5rem;
    }
    .breadcrumbs a { color: #b71c1c; text-decoration: none; font-weight: 500; }
    .breadcrumbs a:hover { text-decoration: underline; }

    .cover-image {
      height: 400px;
      background-size: cover;
      background-position: center;
      border-radius: 16px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
      margin-bottom: 2rem;
    }

    .article-card {
      background: white;
      border-radius: 16px;
      padding: 3rem;
      box-shadow: 0 4px 25px rgba(0,0,0,0.05);
      border-top: 5px solid #b71c1c;
    }

    .meta-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .sport-tag {
      background: #b71c1c;
      color: white;
      font-weight: bold;
      font-size: 0.8rem;
      padding: 0.3rem 0.8rem;
      border-radius: 50px;
    }
    .date { color: #888; font-size: 0.9rem; }

    h1 {
      color: #333;
      font-size: 2.5rem;
      line-height: 1.2;
      margin: 0 0 1rem;
      font-weight: 900;
    }

    .author {
      color: #555;
      font-size: 0.95rem;
      margin-bottom: 2rem;
    }
    
    hr { border: 0; border-top: 1px solid #eee; margin: 2rem 0; }

    .content {
      line-height: 1.8;
      color: #444;
      font-size: 1.1rem;
    }
    .lead-text {
      font-size: 1.25rem;
      font-weight: 500;
      color: #222;
      margin-bottom: 1.5rem;
    }
    .body-text {
      white-space: pre-line; /* conserve les sauts de ligne */
    }

    .footer-actions {
      margin-top: 3rem;
      text-align: center;
      border-top: 1px solid #eee;
      padding-top: 2rem;
    }
    .btn-back {
      display: inline-block;
      padding: 0.75rem 2rem;
      background: #f5f5f5;
      color: #333;
      border-radius: 50px;
      text-decoration: none;
      font-weight: bold;
      font-size: 0.95rem;
      transition: all 0.2s;
    }
    .btn-back:hover {
      background: #e0e0e0;
      transform: translateX(-3px);
    }

    .loading-container { text-align: center; padding: 8rem 0; color: #999; }
    .spinner { font-size: 3rem; margin-bottom: 1rem; animation: spin 2s infinite linear; }
    
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class ActualiteDetailComponent implements OnInit {
  article: any = null;

  route = inject(ActivatedRoute);
  api = inject(ApiService);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('id');
      if (idStr) {
        const id = parseInt(idStr, 10);
        this.api.getArticleById(id).subscribe({
          next: (data) => this.article = data,
          error: () => this.article = null
        });
      }
    });
  }

  // Divise le texte pour simuler un paragraphe d'introduction en gras
  getLeadText(): string {
    if (!this.article) return '';
    const parts = this.article.contenu.split('\n\n');
    return parts[0];
  }

  getBodyText(): string {
    if (!this.article) return '';
    const parts = this.article.contenu.split('\n\n');
    if (parts.length <= 1) return '';
    return parts.slice(1).join('\n\n');
  }
}
