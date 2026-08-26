import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-matches',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './matches.component.html',
  styles: []
})
export class MatchesComponent implements OnInit {
  matches: any[] = [];
  selectedSport = 'ALL';
  activeTab = 'calendar';

  /** §9 — convocations PUBLIÉES par match (site public, lecture anonyme). */
  publicConvocations = new Map<number, any>();
  expandedMatchId: number | null = null;

  api = inject(ApiService);

  ngOnInit() {
    this.loadMatches();
  }

  loadMatches() {
    this.api.getMatches().subscribe({
      next: (data) => {
        this.matches = data;
        // §9 — charge la feuille publique de chaque match à venir
        // (404 silencieuse quand aucune feuille n'a été publiée).
        data.filter((m: any) => m.statut !== 'TERMINE').forEach((m: any) => {
          this.api.getPublicConvocations(m.id).subscribe({
            next: (v) => { if (v) { this.publicConvocations.set(m.id, v); } },
            error: () => {}
          });
        });
      },
      error: () => this.matches = []
    });
  }

  toggleConvocations(matchId: number) {
    this.expandedMatchId = this.expandedMatchId === matchId ? null : matchId;
  }

  filterSport(sport: string) {
    this.selectedSport = sport;
  }

  filteredMatches(): any[] {
    return this.matches.filter(m => {
      if (this.selectedSport !== 'ALL' && m.sport !== this.selectedSport) {
        return false;
      }
      if (this.activeTab === 'calendar') {
        return m.statut !== 'TERMINE';
      } else {
        return m.statut === 'TERMINE';
      }
    }).sort((a, b) => {
      if (this.activeTab === 'calendar') {
        return new Date(a.date).getTime() - new Date(b.date).getTime();
      } else {
        return new Date(b.date).getTime() - new Date(a.date).getTime();
      }
    });
  }
}
