import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-classement',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './classement.component.html',
  styles: []
})
export class ClassementComponent implements OnInit {
  standings: any[] = [];
  competitions: any[] = [];
  selectedComp = '';

  api = inject(ApiService);

  ngOnInit() {
    // Competitions dynamiques : parametre club 'competitions' (source ADMIN)
    this.api.getCompetitions().subscribe({
      next: (data) => {
        this.competitions = Array.isArray(data) ? data : [];
        if (this.competitions.length > 0) {
          this.selectedComp = this.competitions[0].name;
          this.loadStandings();
        }
      },
      error: () => this.competitions = []
    });
  }

  /** Emoji du sport depuis le parametre club (plus de matching par nom). */
  sportEmoji(name: string): string {
    const comp = this.competitions.find(c => c.name === name);
    switch (comp?.sport) {
      case 'BASKETBALL': return '🏀';
      case 'HANDBALL': return '🤾';
      case 'VOLLEYBALL': return '🏐';
      case 'NATATION': return '🏊';
      case 'JUDO': return '🥋';
      case 'ATHLETISME': return '🏃';
      default: return '⚽';
    }
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
}
