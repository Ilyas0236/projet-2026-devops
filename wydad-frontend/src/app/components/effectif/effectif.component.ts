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
            nom: 'Yahya Jabrane',
            numero: 5,
            poste: 'Milieu',
            age: 32,
            matchsJoues: 150,
            buts: 12,
            passes: 8,
          },
          {
            nom: 'Ayoub El Amloud',
            numero: 22,
            poste: 'Défenseur',
            age: 29,
            matchsJoues: 120,
            buts: 5,
            passes: 15,
          },
          {
            nom: 'Youssef El Motie',
            numero: 32,
            poste: 'Gardien',
            age: 28,
            matchsJoues: 60,
            buts: 0,
            passes: 1,
          },
          {
            nom: 'Hamdou El Houni',
            numero: 10,
            poste: 'Attaquant',
            age: 30,
            matchsJoues: 30,
            buts: 8,
            passes: 5,
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
}
