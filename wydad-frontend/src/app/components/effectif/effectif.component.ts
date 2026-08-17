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

  api = inject(ApiService);

  ngOnInit() {
    this.loadPlayers();
  }

  loadPlayers() {
    this.api.getJoueursBySport(this.selectedSport).subscribe({
      next: (data) => {
        this.players = data.sort((a, b) => (a.numero || 99) - (b.numero || 99));
      },
      error: () => {
        // Mock data for display purposes if backend is not fully reachable
        this.players = [
          {
            fullName: 'Yahya Jabrane',
            jerseyNumber: 5,
            position: 'Milieu',
            birthDate: '1991-06-18',
            matchesPlayed: 150,
            goals: 12,
            assists: 8,
          },
          {
            fullName: 'Ayoub El Amloud',
            jerseyNumber: 22,
            position: 'Défenseur',
            birthDate: '1994-04-08',
            matchesPlayed: 120,
            goals: 5,
            assists: 15,
          },
          {
            fullName: 'Youssef El Motie',
            jerseyNumber: 32,
            position: 'Gardien',
            birthDate: '1994-12-16',
            matchesPlayed: 60,
            goals: 0,
            assists: 1,
          },
          {
            fullName: 'Hamdou El Houni',
            jerseyNumber: 10,
            position: 'Attaquant',
            birthDate: '1994-02-12',
            matchesPlayed: 30,
            goals: 8,
            assists: 5,
          },
        ];
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

  getAge(birthDate: string): number {
    if (!birthDate) return 0;
    const diff = Date.now() - new Date(birthDate).getTime();
    return Math.abs(new Date(diff).getUTCFullYear() - 1970);
  }
}
