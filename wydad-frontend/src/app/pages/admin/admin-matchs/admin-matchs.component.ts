import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-admin-matchs',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Gestion des Matchs</h2>
          <p class="text-sm text-gray-400 mt-1">Planifiez les matchs et mettez à jour les scores.</p>
        </div>
        <button (click)="openModal()" class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 font-display font-bold uppercase tracking-wider text-sm skew-x-[-10deg] transition-colors">
          <span class="skew-x-[10deg] block">Nouveau Match</span>
        </button>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="!loading" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10">
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Rencontre</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Date & Heure</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Compétition</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Statut / Score</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let item of matchs" class="hover:bg-white/5 transition-colors">
              <td class="py-3 px-4 text-sm text-white font-bold flex items-center gap-2">
                <img *ngIf="item.adversaireLogoUrl" [src]="apiService.getMediaUrl(item.adversaireLogoUrl)" class="w-8 h-8 object-contain rounded" alt="logo">
                {{ item.homeTeam }} - {{ item.awayTeam }}
              </td>
              <td class="py-3 px-4 text-xs text-gray-400">{{ item.matchDate | date:'medium' }}</td>
              <td class="py-3 px-4 text-xs text-gray-400">{{ item.competition }}</td>
              <td class="py-3 px-4 text-sm font-bold" [ngClass]="{'text-wydad-red': item.statut === 'TERMINE', 'text-yellow-400': item.statut === 'EN_COURS', 'text-gray-400': item.statut === 'A_VENIR'}">
                <span *ngIf="item.statut !== 'A_VENIR'">{{ item.homeScore }} - {{ item.awayScore }}</span>
                <span *ngIf="item.statut === 'A_VENIR'" class="text-xs uppercase font-medium">À venir</span>
              </td>
              <td class="py-3 px-4 text-right">
                <button (click)="openScoreModal(item)" class="text-green-400 hover:text-green-300 mx-2 text-xs uppercase font-bold" *ngIf="item.statut !== 'A_VENIR'">Score</button>
                <button (click)="openModal(item)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteMatch(item.id)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal -->
      <div *ngIf="showModal" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
        <div class="bg-zinc-900 border border-white/10 p-6 w-full max-w-2xl rounded-lg">
          <h3 class="text-xl font-display font-bold text-white uppercase tracking-wider mb-6">
            {{ isEdit ? 'Modifier le Match' : 'Nouveau Match' }}
          </h3>
          
          <div class="grid grid-cols-2 gap-4 mb-4">
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Équipe Domicile</label>
              <input type="text" [(ngModel)]="currentMatch.homeTeam" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Équipe Extérieur</label>
              <input type="text" [(ngModel)]="currentMatch.awayTeam" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Date</label>
              <input type="datetime-local" [(ngModel)]="currentMatch.matchDate" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Lieu</label>
              <input type="text" [(ngModel)]="currentMatch.venue" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Compétition</label>
              <input type="text" [(ngModel)]="currentMatch.competition" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Statut</label>
              <select [(ngModel)]="currentMatch.statut" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
                <option value="A_VENIR">À venir</option>
                <option value="EN_COURS">En cours</option>
                <option value="TERMINE">Terminé</option>
                <option value="REPORTE">Reporté</option>
              </select>
            </div>
          </div>

          <!-- Upload logo adversaire -->
          <div class="mb-4">
            <label class="block text-xs text-gray-400 uppercase mb-1">Logo Adversaire</label>
            <div class="flex items-center gap-4">
              <label class="cursor-pointer bg-zinc-800 border border-white/10 hover:border-wydad-red text-white px-4 py-2 rounded text-sm transition-colors">
                📷 Choisir une image
                <input type="file" accept="image/*" (change)="uploadLogo($event)" class="hidden">
              </label>
              <img *ngIf="currentMatch.adversaireLogoUrl" [src]="apiService.getMediaUrl(currentMatch.adversaireLogoUrl)" class="w-12 h-12 object-contain rounded border border-white/10" alt="preview">
              <span *ngIf="uploading" class="text-xs text-yellow-400 animate-pulse">Envoi en cours...</span>
            </div>
          </div>
          
          <div class="mt-8 flex justify-end gap-3">
            <button (click)="closeModal()" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveMatch()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Sauvegarder</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminMatchsComponent implements OnInit {
  matchs: any[] = [];
  loading = true;
  showModal = false;
  isEdit = false;
  currentMatch: any = {};

  constructor(public apiService: ApiService) {}

  ngOnInit() {
    this.loadMatchs();
  }

  loadMatchs() {
    this.loading = true;
    this.apiService.getMatches().subscribe({
      next: (data) => {
        this.matchs = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });
  }

  openModal(match?: any) {
    if (match) {
      this.isEdit = true;
      this.currentMatch = { ...match };
      if(this.currentMatch.matchDate) {
         this.currentMatch.matchDate = new Date(this.currentMatch.matchDate).toISOString().slice(0, 16);
      }
    } else {
      this.isEdit = false;
      this.currentMatch = { homeTeam: 'Wydad AC', awayTeam: '', matchDate: '', venue: 'Stade Mohammed V', competition: 'Botola Pro', statut: 'A_VENIR', adversaireLogoUrl: '' };
    }
    this.showModal = true;
  }

  uploading = false;

  uploadLogo(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    this.uploading = true;
    this.apiService.uploadMedia(file).subscribe({
      next: (res) => {
        this.currentMatch.adversaireLogoUrl = res.url;
        this.uploading = false;
      },
      error: () => {
        this.uploading = false;
        alert('Erreur lors de l\'upload du logo.');
      }
    });
  }

  openScoreModal(match: any) {
    const homeScore = prompt(`Score pour ${match.homeTeam}`, match.homeScore || 0);
    const awayScore = prompt(`Score pour ${match.awayTeam}`, match.awayScore || 0);
    if(homeScore !== null && awayScore !== null) {
      const resultData = { homeScore: parseInt(homeScore), awayScore: parseInt(awayScore), statut: match.statut };
      this.apiService.updateMatchResult(match.id, resultData).subscribe(() => this.loadMatchs());
    }
  }

  closeModal() {
    this.showModal = false;
  }

  saveMatch() {
    if (this.isEdit) {
      this.apiService.updateMatch(this.currentMatch.id, this.currentMatch).subscribe(() => {
        this.loadMatchs();
        this.closeModal();
      });
    } else {
      this.apiService.createMatch(this.currentMatch).subscribe(() => {
        this.loadMatchs();
        this.closeModal();
      });
    }
  }

  deleteMatch(id: number) {
    if (confirm('Voulez-vous vraiment supprimer ce match ?')) {
      this.apiService.deleteMatch(id).subscribe(() => this.loadMatchs());
    }
  }
}
