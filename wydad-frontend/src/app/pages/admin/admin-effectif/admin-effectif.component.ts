import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

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
  /** Catégorie du filtre effectif — toutes les catégories U15→SENIOR sont
   * interrogeables (vision ADMIN), plus de SENIOR codé en dur. */
  readonly categories = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];
  selectedCategoryFilter = 'SENIOR';
  isSaving = false;
  saveError = '';

  // Formulaire complet : Compte + Profil Sportif
  newPlayer = {
    // Compte utilisateur (auth-service)
    email: '',
    password: '',
    // Profil sportif (sports-service)
    fullName: '',
    sportType: 'FOOTBALL',
    category: 'SENIOR',
    position: 'Milieu',
    jerseyNumber: 99,
    nationality: 'Maroc',
    height: null as number | null,
    weight: null as number | null,
    birthDate: '1995-01-01',
    matchesPlayed: 0,
    goals: 0,
    assists: 0,
    photoUrl: ''
  };

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadPlayers();
  }

  loadPlayers() {
    this.loading = true;
    this.api.getPlayersByCategory(this.selectedSportFilter, this.selectedCategoryFilter).subscribe({
      next: (data) => {
        this.players = data.sort((a: any, b: any) => (a.jerseyNumber || 99) - (b.jerseyNumber || 99));
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
      email: '',
      password: '',
      fullName: '',
      sportType: this.selectedSportFilter,
      category: 'SENIOR',
      position: 'Milieu',
      jerseyNumber: 99,
      nationality: 'Maroc',
      height: null,
      weight: null,
      birthDate: '1995-01-01',
      matchesPlayed: 0,
      goals: 0,
      assists: 0,
      photoUrl: ''
    };
    this.saveError = '';
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
    this.saveError = '';
  }

  savePlayer() {
    if (!this.newPlayer.email || !this.newPlayer.password || !this.newPlayer.fullName) {
      this.saveError = 'Veuillez remplir tous les champs obligatoires (Email, Mot de passe, Nom complet).';
      return;
    }
    this.isSaving = true;
    this.saveError = '';

    const nameParts = this.newPlayer.fullName.split(' ');
    const firstName = nameParts[0] || '';
    const lastName = nameParts.slice(1).join(' ') || firstName;

    // Étape 1: Créer le compte utilisateur avec le rôle JOUEUR
    this.api.adminCreateUser({
      email: this.newPlayer.email,
      password: this.newPlayer.password,
      firstName: firstName,
      lastName: lastName,
      role: 'JOUEUR'
    }).subscribe({
      next: (createdUser: any) => {
        // Étape 2: Créer le profil sportif lié à ce compte
        const playerPayload = {
          userId: createdUser.id,
          fullName: this.newPlayer.fullName,
          sportType: this.newPlayer.sportType,
          category: this.newPlayer.category,
          position: this.newPlayer.position,
          jerseyNumber: this.newPlayer.jerseyNumber,
          nationality: this.newPlayer.nationality,
          height: this.newPlayer.height,
          weight: this.newPlayer.weight,
          birthDate: this.newPlayer.birthDate,
          matchesPlayed: this.newPlayer.matchesPlayed,
          goals: this.newPlayer.goals,
          assists: this.newPlayer.assists,
          photoUrl: this.newPlayer.photoUrl
        };

        this.api.createPlayer(playerPayload).subscribe({
          next: () => {
            this.isSaving = false;
            this.closeAddModal();
            this.loadPlayers();
          },
          error: (err) => {
            console.error('Erreur création profil sportif', err);
            this.saveError = 'Compte créé mais erreur lors de la création du profil sportif. Veuillez réessayer.';
            this.isSaving = false;
          }
        });
      },
      error: (err) => {
        console.error('Erreur création compte', err);
        if (err.status === 409 || err.error?.message?.includes('existe')) {
          this.saveError = 'Cet email est déjà utilisé par un autre compte.';
        } else {
          this.saveError = 'Erreur lors de la création du compte utilisateur.';
        }
        this.isSaving = false;
      }
    });
  }

  async deletePlayer(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Retirer le joueur',
      message: 'Voulez-vous retirer ce joueur de l\'effectif ?',
      confirmLabel: 'Retirer',
      danger: true
    });
    if (!ok) return;
    this.api.deletePlayer(id).subscribe({
      next: () => this.loadPlayers(),
      error: (err) => console.error('Erreur suppression', err)
    });
  }

  uploadingPhoto = false;

  uploadPhoto(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    
    this.uploadingPhoto = true;
    this.api.uploadMedia(file).subscribe({
      next: (res) => {
        this.newPlayer.photoUrl = res.url;
        this.uploadingPhoto = false;
      },
      error: (err) => {
        console.error('Erreur upload', err);
        this.uploadingPhoto = false;
        this.toast.error('Erreur lors du chargement de la photo.');
      }
    });
  }
}
