import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-matches',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-header">
      <h1>🗓️ Matchs & Résultats</h1>
      <p>Suivez toutes les rencontres du Wydad AC dans toutes les disciplines</p>
    </div>

    <div class="container">
      <!-- FILTRES SPORTS -->
      <div class="filters">
        <button (click)="filterSport('ALL')" [class.active]="selectedSport === 'ALL'">Tous les Sports</button>
        <button (click)="filterSport('FOOTBALL')" [class.active]="selectedSport === 'FOOTBALL'">⚽ Football</button>
        <button (click)="filterSport('BASKETBALL')" [class.active]="selectedSport === 'BASKETBALL'">🏀 Basketball</button>
        <button (click)="filterSport('HANDBALL')" [class.active]="selectedSport === 'HANDBALL'">🤾 Handball</button>
      </div>

      <!-- TABS CALENDRIER / RESULTATS -->
      <div class="tabs">
        <button (click)="activeTab = 'calendar'" [class.active]="activeTab === 'calendar'">📅 Calendrier (Matchs à venir)</button>
        <button (click)="activeTab = 'results'" [class.active]="activeTab === 'results'">🏆 Résultats (Matchs terminés)</button>
      </div>

      <!-- CALENDAR TAB -->
      <div *ngIf="activeTab === 'calendar'" class="matches-grid">
        <div class="match-card" *ngFor="let match of filteredMatches()">
          <div class="match-header">
            <span class="competition">{{ match.competition }}</span>
            <span class="sport-badge">{{ match.sport }}</span>
          </div>
          <div class="teams-line">
            <div class="team home">WYDAD AC</div>
            <div class="vs">VS</div>
            <div class="team away">{{ match.adversaire }}</div>
          </div>
          <div class="match-footer">
            <div class="detail-item">🕒 {{ match.date | date:'dd/MM/yyyy' }} à {{ match.heure ? match.heure.substring(0, 5) : 'A confirmer' }}</div>
            <div class="detail-item">🏟️ {{ match.lieu }}</div>
            <div class="status-tag" [class]="match.statut">{{ match.statut }}</div>
          </div>
        </div>
        <div *ngIf="filteredMatches().length === 0" class="empty-msg">
          Aucun match à venir programmé dans cette section.
        </div>
      </div>

      <!-- RESULTS TAB -->
      <div *ngIf="activeTab === 'results'" class="matches-grid">
        <div class="match-card result-card" *ngFor="let match of filteredMatches()">
          <div class="match-header">
            <span class="competition">{{ match.competition }}</span>
            <span class="sport-badge">{{ match.sport }}</span>
          </div>
          <div class="teams-line result-line">
            <div class="team-result">
              <span class="team home">WYDAD AC</span>
              <span class="score" [class.win]="match.scoreWydad > match.scoreAdversaire" [class.loss]="match.scoreWydad < match.scoreAdversaire">
                {{ match.scoreWydad }}
              </span>
            </div>
            <div class="dash">-</div>
            <div class="team-result">
              <span class="score" [class.win]="match.scoreAdversaire > match.scoreWydad" [class.loss]="match.scoreAdversaire < match.scoreWydad">
                {{ match.scoreAdversaire }}
              </span>
              <span class="team away">{{ match.adversaire }}</span>
            </div>
          </div>
          <div class="match-footer">
            <div class="detail-item">🕒 Joué le {{ match.date | date:'dd/MM/yyyy' }}</div>
            <div class="detail-item">🏟️ {{ match.lieu }}</div>
            <span class="outcome-tag" [class.win]="match.scoreWydad > match.scoreAdversaire" [class.draw]="match.scoreWydad === match.scoreAdversaire" [class.loss]="match.scoreWydad < match.scoreAdversaire">
              {{ match.scoreWydad > match.scoreAdversaire ? 'Victoire' : match.scoreWydad === match.scoreAdversaire ? 'Nul' : 'Défaite' }}
            </span>
          </div>
        </div>
        <div *ngIf="filteredMatches().length === 0" class="empty-msg">
          Aucun résultat disponible dans cette section.
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(90deg, #b71c1c, #8e0000);
      color: white;
      padding: 3rem 2rem;
      text-align: center;
    }
    .page-header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; }
    .page-header p { opacity: 0.9; font-size: 1.1rem; }

    .container { max-width: 1100px; margin: 3rem auto; padding: 0 2rem; }

    /* FILTERS */
    .filters {
      display: flex;
      gap: 0.75rem;
      margin-bottom: 2rem;
      flex-wrap: wrap;
      justify-content: center;
    }
    .filters button {
      padding: 0.6rem 1.25rem;
      border: 2px solid #ddd;
      background: white;
      border-radius: 50px;
      cursor: pointer;
      font-weight: bold;
      font-size: 0.9rem;
      transition: all 0.2s;
      color: #555;
    }
    .filters button:hover, .filters button.active {
      background: #b71c1c;
      color: white;
      border-color: #b71c1c;
    }

    /* TABS */
    .tabs {
      display: flex;
      gap: 1rem;
      border-bottom: 2px solid #eee;
      margin-bottom: 2.5rem;
      justify-content: center;
    }
    .tabs button {
      padding: 1rem 1.5rem;
      background: transparent;
      border: none;
      font-size: 1.05rem;
      font-weight: bold;
      color: #666;
      cursor: pointer;
      border-bottom: 3px solid transparent;
      transition: all 0.2s;
    }
    .tabs button:hover, .tabs button.active {
      color: #b71c1c;
      border-bottom-color: #b71c1c;
    }

    /* GRID & CARDS */
    .matches-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 2rem;
    }
    .match-card {
      background: white;
      border-radius: 16px;
      padding: 1.5rem;
      box-shadow: 0 4px 15px rgba(0,0,0,0.06);
      border-top: 4px solid #b71c1c;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .result-card { border-top-color: #333; }
    
    .match-header {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      font-weight: bold;
      color: #888;
    }
    .competition {
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .sport-badge {
      background: #eee;
      color: #333;
      padding: 0.15rem 0.5rem;
      border-radius: 4px;
    }

    .teams-line {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      font-size: 1.25rem;
      font-weight: bold;
      color: #333;
      margin: 0.5rem 0;
    }
    .teams-line .home { color: #b71c1c; }
    .teams-line .vs { font-style: italic; color: #aaa; font-size: 1rem; }

    .result-line {
      flex-direction: column;
      gap: 0.5rem;
      align-items: stretch;
    }
    .team-result {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .score {
      background: #eee;
      color: #333;
      width: 32px;
      height: 32px;
      line-height: 32px;
      text-align: center;
      border-radius: 6px;
      font-weight: bold;
    }
    .score.win { background: #e8f5e9; color: #2e7d32; }
    .score.loss { background: #ffebee; color: #c62828; }
    .dash { text-align: center; color: #ccc; font-weight: normal; }

    .match-footer {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      font-size: 0.85rem;
      color: #666;
      border-top: 1px solid #eee;
      padding-top: 0.75rem;
      position: relative;
    }
    .detail-item { display: flex; align-items: center; gap: 0.25rem; }
    
    .status-tag {
      position: absolute;
      right: 0;
      bottom: 0;
      font-size: 0.75rem;
      font-weight: bold;
      padding: 0.2rem 0.6rem;
      border-radius: 4px;
    }
    .status-tag.PROGRAMME { background: #e3f2fd; color: #1565c0; }
    .status-tag.EN_COURS { background: #fff3e0; color: #e65100; animation: blink 1s infinite alternate; }
    .status-tag.REPORTE { background: #efebe9; color: #4e342e; }

    .outcome-tag {
      position: absolute;
      right: 0;
      bottom: 0;
      font-size: 0.75rem;
      font-weight: bold;
      padding: 0.2rem 0.6rem;
      border-radius: 4px;
    }
    .outcome-tag.win { background: #e8f5e9; color: #2e7d32; }
    .outcome-tag.draw { background: #eceff1; color: #546e7a; }
    .outcome-tag.loss { background: #ffebee; color: #c62828; }

    .empty-msg {
      grid-column: 1 / -1;
      text-align: center;
      padding: 4rem;
      color: #999;
      background: white;
      border-radius: 12px;
      box-shadow: 0 4px 15px rgba(0,0,0,0.04);
    }

    @keyframes blink {
      from { opacity: 0.6; }
      to { opacity: 1; }
    }
  `]
})
export class MatchesComponent implements OnInit {
  matches: any[] = [];
  selectedSport = 'ALL';
  activeTab = 'calendar'; // 'calendar' ou 'results'

  api = inject(ApiService);

  ngOnInit() {
    this.loadMatches();
  }

  loadMatches() {
    this.api.getMatches().subscribe({
      next: (data) => {
        this.matches = data;
      },
      error: () => this.matches = []
    });
  }

  filterSport(sport: string) {
    this.selectedSport = sport;
  }

  filteredMatches(): any[] {
    return this.matches.filter(m => {
      // Filtrer par sport
      if (this.selectedSport !== 'ALL' && m.sport !== this.selectedSport) {
        return false;
      }
      // Filtrer par tab
      if (this.activeTab === 'calendar') {
        return m.statut !== 'TERMINE';
      } else {
        return m.statut === 'TERMINE';
      }
    }).sort((a, b) => {
      if (this.activeTab === 'calendar') {
        // Matchs à venir : plus proches d'abord
        return new Date(a.date).getTime() - new Date(b.date).getTime();
      } else {
        // Résultats : plus récents d'abord
        return new Date(b.date).getTime() - new Date(a.date).getTime();
      }
    });
  }
}
