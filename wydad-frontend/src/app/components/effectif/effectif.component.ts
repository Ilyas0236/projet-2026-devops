import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-effectif',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './effectif.component.html',
})
export class EffectifComponent implements OnInit {
  players: any[] = [];
  selectedSport = 'FOOTBALL';
  loadError = false;
  loading = true;
  /** Saison en cours depuis la configuration club (source de verite ADMIN). */
  saison = '';

  api = inject(ApiService);

  ngOnInit() {
    this.api.getClubSetting('club_info').subscribe({
      next: (info) => {
        this.saison = info?.saison || '';
      },
      error: () => {
        this.saison = '';
      }
    });
    this.loadPlayers();
  }

  loadPlayers() {
    this.loading = true;
    this.loadError = false;
    this.api.getJoueursBySport(this.selectedSport).subscribe({
      next: (data) => {
        this.players = (data || []).sort((a, b) => (a.numero || a.jerseyNumber || 99) - (b.numero || b.jerseyNumber || 99));
        this.loading = false;
      },
      error: () => {
        // Pas de fausses données : on affiche un état d'erreur explicite
        this.players = [];
        this.loadError = true;
        this.loading = false;
      },
    });
  }

  changeSport(sport: string) {
    this.selectedSport = sport;
    this.loadPlayers();
  }

  getGoalLabel(): string {
    return this.selectedSport === 'BASKETBALL' ? 'Points' : 'Buts';
  }
}
