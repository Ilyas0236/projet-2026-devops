import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-admin-effectif',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-effectif.component.html'
})
export class AdminEffectifComponent implements OnInit {
  players: any[] = [];
  loading = true;
  showModal = false;
  selectedSportFilter = 'FOOTBALL';
  
  newPlayer = {
    nom: '',
    prenom: '',
    dateNaissance: '1995-01-01',
    age: 28,
    nationalite: 'Maroc',
    sport: 'FOOTBALL',
    role: 'PLAYER',
    poste: 'Milieu',
    numero: 10,
    matchsJoues: 0,
    buts: 0,
    passes: 0
  };

  api = inject(ApiService);

  ngOnInit() {
    this.loadPlayers();
  }

  loadPlayers() {
    this.loading = true;
    this.api.getJoueursBySport(this.selectedSportFilter).subscribe({
      next: (data) => {
        this.players = data.sort((a, b) => (a.numero || 99) - (b.numero || 99));
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement joueurs', err);
        this.loading = false;
      }
    });
  }

  openAddModal() {
    this.newPlayer = {
      nom: '',
      prenom: '',
      dateNaissance: '1995-01-01',
      age: 25,
      nationalite: 'Maroc',
      sport: this.selectedSportFilter,
      role: 'PLAYER',
      poste: 'Milieu',
      numero: 99,
      matchsJoues: 0,
      buts: 0,
      passes: 0
    };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  savePlayer() {
    // Basic split for nom/prenom to respect entity
    const parts = this.newPlayer.nom.split(' ');
    this.newPlayer.prenom = parts[0] || '';
    
    this.api.createPlayer(this.newPlayer).subscribe({
      next: (res) => {
        this.loadPlayers();
        this.closeAddModal();
      },
      error: (err) => {
        console.error('Erreur création joueur', err);
        alert('Erreur lors de la création du joueur');
      }
    });
  }

  deletePlayer(id: number) {
    if (confirm('Voulez-vous retirer ce joueur de l\'effectif ?')) {
      this.api.deletePlayer(id).subscribe({
        next: () => this.loadPlayers(),
        error: (err) => console.error('Erreur suppression', err)
      });
    }
  }
}
