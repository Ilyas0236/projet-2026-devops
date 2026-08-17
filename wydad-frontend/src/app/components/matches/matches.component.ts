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
