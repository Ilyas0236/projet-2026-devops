import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <!-- HERO SECTION -->
    <section class="hero">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <h1>WYDAD ATHLETIC CLUB</h1>
        <p class="subtitle">Depuis 1937 — Le plus grand club du Maroc et d'Afrique</p>
        <div class="hero-buttons">
          <a routerLink="/actualites" class="btn-primary">📰 Actualités</a>
          <a routerLink="/login" class="btn-secondary">🔴 Espace Adhérent</a>
        </div>
      </div>
    </section>

    <!-- PROCHAIN MATCH -->
    <section class="next-match" *ngIf="nextMatch">
      <div class="container">
        <h2>🔥 Prochain Match</h2>
        <div class="match-card">
          <div class="teams-container">
            <div class="team home">WYDAD AC</div>
            <div class="vs">VS</div>
            <div class="team away">{{ nextMatch.adversaire }}</div>
          </div>
          <div class="match-info">
            <span>📅 {{ nextMatch.date | date:'dd MMMM yyyy' }} à {{ nextMatch.heure ? nextMatch.heure.substring(0, 5) : '' }}</span>
            <span>🏟️ {{ nextMatch.lieu }}</span>
            <span>🏆 {{ nextMatch.competition }}</span>
            <span class="sport-tag">{{ nextMatch.sport }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ACTUALITÉS -->
    <section class="news">
      <div class="container">
        <h2>📰 Dernières Actualités</h2>
        <div class="news-grid">
          <div class="news-card" *ngFor="let article of articles" [routerLink]="['/actualites', article.id]">
            <div class="news-image" [style.backgroundImage]="'url(' + (article.imageUrl || 'https://via.placeholder.com/400x250/d32f2f/ffffff?text=WYDAD') + ')'"></div>
            <div class="news-body">
              <span class="sport-tag">{{ article.sport }}</span>
              <h3>{{ article.titre }}</h3>
              <p>{{ article.contenu | slice:0:120 }}...</p>
              <small>Par {{ article.auteur }} — {{ article.createdAt | date:'dd/MM/yyyy' }}</small>
            </div>
          </div>
        </div>
        <div class="text-center" *ngIf="articles.length === 0">
          <p>Aucune actualité pour le moment.</p>
        </div>
      </div>
    </section>

    <!-- SECTION ADHÉSION -->
    <section class="membership">
      <div class="container">
        <h2>🎟️ Deviens Adhérent</h2>
        <div class="cards">
          <div class="member-card rouge">
            <h3>ROUGE</h3>
            <div class="price">500 DH/an</div>
            <ul>
              <li>✅ Carte membre digitale</li>
              <li>✅ Actualités premium</li>
              <li>✅ Réductions partenaires</li>
            </ul>
            <a routerLink="/register" class="btn-subscribe">Rejoindre</a>
          </div>
          <div class="member-card or">
            <h3>OR</h3>
            <div class="price">1 200 DH/an</div>
            <ul>
              <li>✅ Tout Rouge +</li>
              <li>✅ Stats avancées</li>
              <li>✅ Priorité billetterie</li>
            </ul>
            <a routerLink="/register" class="btn-subscribe">Rejoindre</a>
          </div>
          <div class="member-card diamant">
            <h3>DIAMANT</h3>
            <div class="price">3 000 DH/an</div>
            <ul>
              <li>✅ Tout Or +</li>
              <li>✅ Accès VOD complet</li>
              <li>✅ Événements exclusifs</li>
            </ul>
            <a routerLink="/register" class="btn-subscribe">Rejoindre</a>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .hero {
      position: relative;
      height: 85vh;
      background: linear-gradient(135deg, #b71c1c 0%, #7f0000 50%, #000000 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      text-align: center;
      color: white;
      overflow: hidden;
    }
    .hero-overlay {
      position: absolute;
      inset: 0;
      background: url('https://www.transparenttextures.com/patterns/black-scales.png');
      opacity: 0.3;
    }
    .hero-content {
      position: relative;
      z-index: 1;
      max-width: 800px;
      padding: 2rem;
    }
    .hero h1 {
      font-size: 4rem;
      font-weight: 900;
      letter-spacing: 4px;
      margin-bottom: 1rem;
      text-shadow: 2px 2px 8px rgba(0,0,0,0.5);
    }
    .subtitle {
      font-size: 1.3rem;
      opacity: 0.9;
      margin-bottom: 2rem;
    }
    .hero-buttons {
      display: flex;
      gap: 1rem;
      justify-content: center;
      flex-wrap: wrap;
    }
    .btn-primary, .btn-secondary, .btn-subscribe {
      padding: 1rem 2rem;
      border-radius: 50px;
      text-decoration: none;
      font-weight: bold;
      transition: all 0.3s;
      border: none;
      cursor: pointer;
      display: inline-block;
    }
    .btn-primary {
      background: white;
      color: #b71c1c;
    }
    .btn-primary:hover {
      transform: translateY(-3px);
      box-shadow: 0 10px 25px rgba(0,0,0,0.3);
    }
    .btn-secondary {
      background: transparent;
      color: white;
      border: 2px solid white;
    }
    .btn-secondary:hover {
      background: white;
      color: #b71c1c;
    }

    .container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 2rem;
    }
    section {
      padding: 4rem 0;
    }
    h2 {
      text-align: center;
      font-size: 2.2rem;
      color: #b71c1c;
      margin-bottom: 2.5rem;
    }

    .next-match {
      background: #f8f9fa;
    }
    .match-card {
      background: white;
      border-radius: 16px;
      padding: 2.5rem;
      text-align: center;
      box-shadow: 0 8px 30px rgba(0,0,0,0.1);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1.5rem;
      border-top: 5px solid #d32f2f;
    }
    .teams-container {
      display: flex;
      align-items: center;
      gap: 2rem;
      justify-content: center;
    }
    .team {
      font-size: 1.8rem;
      font-weight: bold;
    }
    .team.home { color: #d32f2f; }
    .vs {
      font-size: 2rem;
      font-weight: 900;
      color: #666;
    }
    .match-info {
      display: flex;
      gap: 1.5rem;
      color: #666;
      flex-wrap: wrap;
      justify-content: center;
      align-items: center;
      font-size: 0.95rem;
    }
    .match-info span {
      background: #f1f1f1;
      padding: 0.4rem 1rem;
      border-radius: 50px;
    }

    .news { background: white; }
    .news-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 2rem;
    }
    .news-card {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 4px 15px rgba(0,0,0,0.08);
      transition: transform 0.3s;
      cursor: pointer;
    }
    .news-card:hover {
      transform: translateY(-5px);
      box-shadow: 0 8px 25px rgba(0,0,0,0.15);
    }
    .news-image {
      height: 200px;
      background-size: cover;
      background-position: center;
    }
    .news-body {
      padding: 1.5rem;
    }
    .sport-tag {
      background: #d32f2f;
      color: white;
      padding: 0.25rem 0.75rem;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: bold;
    }
    .news-body h3 {
      margin: 0.75rem 0;
      color: #333;
      font-size: 1.2rem;
    }
    .news-body p {
      color: #666;
      line-height: 1.5;
    }
    .news-body small {
      color: #999;
    }

    .membership {
      background: linear-gradient(180deg, #f8f9fa 0%, #fff 100%);
    }
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 2rem;
    }
    .member-card {
      background: white;
      border-radius: 16px;
      padding: 2rem;
      text-align: center;
      box-shadow: 0 4px 20px rgba(0,0,0,0.08);
      border-top: 4px solid;
      transition: transform 0.3s;
    }
    .member-card:hover { transform: translateY(-5px); }
    .member-card.rouge { border-color: #d32f2f; }
    .member-card.or { border-color: #ffc107; }
    .member-card.diamant { border-color: #e0e0e0; }
    .member-card h3 { font-size: 1.5rem; margin-bottom: 0.5rem; }
    .price { font-size: 2rem; font-weight: bold; color: #b71c1c; margin-bottom: 1rem; }
    .member-card ul { list-style: none; padding: 0; margin: 1.5rem 0; text-align: left; }
    .member-card li { padding: 0.5rem 0; color: #555; }
    .btn-subscribe {
      background: linear-gradient(90deg, #d32f2f, #b71c1c);
      color: white;
      width: 100%;
    }
    .btn-subscribe:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(211,47,47,0.4);
    }

    .text-center { text-align: center; color: #666; }
  `]
})
export class HomeComponent implements OnInit {
  articles: any[] = [];
  nextMatch: any = null;

  api = inject(ApiService);

  ngOnInit() {
    this.loadArticles();
    this.loadNextMatch();
  }

  loadArticles() {
    this.api.getArticles().subscribe({
      next: (data) => this.articles = data.slice(0, 6),
      error: () => this.articles = []
    });
  }

  loadNextMatch() {
    this.api.getMatchesByStatut('PROGRAMME').subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          // Trier par date pour obtenir le plus proche chronologiquement
          const sorted = data.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
          this.nextMatch = sorted[0];
        } else {
          this.nextMatch = null;
        }
      },
      error: () => {
        this.nextMatch = null;
      }
    });
  }
}