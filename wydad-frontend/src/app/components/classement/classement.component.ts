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
