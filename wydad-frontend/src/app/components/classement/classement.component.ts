import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-classement',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-header">
      <h1>🏆 Classements</h1>
      <p>Retrouvez la position du Wydad AC dans ses différentes compétitions</p>
    </div>

    <div class="container">
      <!-- SELECTEUR DE COMPETITION -->
      <div class="competition-selector">
        <button (click)="changeCompetition('Botola Pro')" [class.active]="selectedComp === 'Botola Pro'">⚽ Botola Pro</button>
        <button (click)="changeCompetition('D1 Basketball')" [class.active]="selectedComp === 'D1 Basketball'">🏀 D1 Basketball</button>
        <button (click)="changeCompetition('Division Excellence')" [class.active]="selectedComp === 'Division Excellence'">🤾 Elite Handball</button>
      </div>

      <!-- TABLE CONTAINER -->
      <div class="table-card">
        <div class="table-header">
          <h3>{{ selectedComp }} — Classement officiel</h3>
          <span class="sport-badge">{{ getSportBadge() }}</span>
        </div>
        
        <div class="table-responsive">
          <table>
            <thead>
              <tr>
                <th class="col-pos">Pos</th>
                <th class="col-team">Équipe</th>
                <th>MJ</th>
                <th>G</th>
                <th>N</th>
                <th>P</th>
                <th class="col-goals">Buts</th>
                <th>Diff</th>
                <th class="col-pts">Pts</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of standings" [class.highlight-wac]="isWac(row.equipe)">
                <td class="col-pos-val">{{ row.position }}</td>
                <td class="col-team-val">
                  <span *ngIf="isWac(row.equipe)" class="wac-star">⭐</span>
                  <strong>{{ row.equipe }}</strong>
                </td>
                <td>{{ row.joues }}</td>
                <td>{{ row.gagnes }}</td>
                <td>{{ row.nuls }}</td>
                <td>{{ row.perdus }}</td>
                <td class="col-goals-val">{{ row.bp }}:{{ row.bc }}</td>
                <td [class.positive]="row.bp - row.bc > 0" [class.negative]="row.bp - row.bc < 0">
                  {{ (row.bp - row.bc > 0 ? '+' : '') }}{{ row.bp - row.bc }}
                </td>
                <td class="col-pts-val">{{ row.points }}</td>
              </tr>
              <tr *ngIf="standings.length === 0">
                <td colspan="9" class="empty-row">Aucun classement disponible pour cette compétition.</td>
              </tr>
            </tbody>
          </table>
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

    .container { max-width: 1000px; margin: 3rem auto; padding: 0 2rem; }

    /* COMPETITION SELECTOR */
    .competition-selector {
      display: flex;
      gap: 0.75rem;
      margin-bottom: 2rem;
      justify-content: center;
      flex-wrap: wrap;
    }
    .competition-selector button {
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
    .competition-selector button:hover, .competition-selector button.active {
      background: #b71c1c;
      color: white;
      border-color: #b71c1c;
    }

    /* TABLE CARD */
    .table-card {
      background: white;
      border-radius: 16px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.06);
      overflow: hidden;
      border-top: 5px solid #b71c1c;
    }
    .table-header {
      padding: 1.5rem 2rem;
      border-bottom: 1px solid #eee;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .table-header h3 { color: #333; margin: 0; font-size: 1.25rem; }
    .sport-badge {
      background: #ffebee;
      color: #b71c1c;
      font-weight: bold;
      font-size: 0.8rem;
      padding: 0.25rem 0.75rem;
      border-radius: 50px;
    }

    .table-responsive {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: center;
      font-size: 0.95rem;
    }
    th, td {
      padding: 1rem;
      border-bottom: 1px solid #eee;
      color: #555;
    }
    th {
      background: #fdfdfd;
      font-weight: bold;
      color: #333;
    }

    .col-pos { width: 60px; }
    .col-team { text-align: left; }
    .col-goals { width: 100px; }
    .col-pts { width: 80px; }

    .col-pos-val { font-weight: bold; color: #999; }
    .col-team-val { text-align: left; color: #333; display: flex; align-items: center; gap: 0.4rem; }
    .wac-star { font-size: 1rem; }
    
    .col-goals-val { color: #777; }
    
    .col-pts-val { font-weight: 800; font-size: 1.05rem; color: #111; }

    .positive { color: #2e7d32 !important; font-weight: 500; }
    .negative { color: #c62828 !important; font-weight: 500; }

    /* HIGHLIGHT WAC */
    .highlight-wac {
      background: #fff5f5;
    }
    .highlight-wac td {
      color: #b71c1c;
      border-bottom: 1px solid #ffcdd2;
    }
    .highlight-wac .col-pos-val { color: #b71c1c; }
    .highlight-wac .col-pts-val { color: #b71c1c; font-size: 1.15rem; }

    .empty-row { padding: 3rem; color: #999; font-style: italic; }
  `]
})
export class ClassementComponent implements OnInit {
  standings: any[] = [];
  selectedComp = 'Botola Pro';

  api = inject(ApiService);

  ngOnInit() {
    this.loadStandings();
  }

  loadStandings() {
    this.api.getClassements(this.selectedComp).subscribe({
      next: (data) => {
        // Trier par position croissante
        this.standings = data.sort((a, b) => a.position - b.position);
      },
      error: () => this.standings = []
    });
  }

  changeCompetition(comp: string) {
    this.selectedComp = comp;
    this.loadStandings();
  }

  isWac(name: string): boolean {
    if (!name) return false;
    const clean = name.toLowerCase();
    return clean.includes('wydad') || clean.includes('wac');
  }

  getSportBadge(): string {
    if (this.selectedComp.includes('Basketball')) return '🏀 Basketball';
    if (this.selectedComp.includes('Handball') || this.selectedComp.includes('Excellence')) return '🤾 Handball';
    return '⚽ Football';
  }
}
