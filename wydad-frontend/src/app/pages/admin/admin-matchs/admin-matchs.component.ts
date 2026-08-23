import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

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
                <img *ngIf="item.imageUrl" [src]="apiService.getMediaUrl(item.imageUrl)" class="w-8 h-8 object-contain rounded" alt="logo">
                WAC - {{ item.adversaire }}
                <span *ngIf="item.sport && item.sport !== 'FOOTBALL'" class="px-2 py-1 bg-white/10 rounded text-[10px] font-normal">{{ item.sport }}</span>
              </td>
              <td class="py-3 px-4 text-xs text-gray-400">{{ item.date | date:'dd/MM/yyyy' }} {{ item.heure }}</td>
              <td class="py-3 px-4 text-xs text-gray-400">{{ item.competition }}</td>
              <td class="py-3 px-4 text-sm font-bold" [ngClass]="{'text-wydad-red': item.statut === 'TERMINE', 'text-yellow-400': item.statut === 'EN_COURS', 'text-gray-400': item.statut !== 'TERMINE' && item.statut !== 'EN_COURS'}">
                <span *ngIf="item.statut === 'PROGRAMME'" class="text-xs uppercase font-medium">À venir</span>
                <span *ngIf="item.statut !== 'PROGRAMME'">{{ item.scoreWydad }} - {{ item.scoreAdversaire }}</span>
                <span *ngIf="item.statut === 'REPORTE'" class="text-xs uppercase font-medium ml-2">(reporté)</span>
              </td>
              <td class="py-3 px-4 text-right">
                <button (click)="openScoreModal(item)" class="text-green-400 hover:text-green-300 mx-2 text-xs uppercase font-bold" *ngIf="item.statut !== 'PROGRAMME'">Score</button>
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
              <label class="block text-xs text-gray-400 uppercase mb-1">Adversaire *</label>
              <input type="text" [(ngModel)]="currentMatch.adversaire" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Sport *</label>
              <select [(ngModel)]="currentMatch.sport" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
                <option value="FOOTBALL">Football</option>
                <option value="BASKETBALL">Basketball</option>
                <option value="HANDBALL">Handball</option>
                <option value="VOLLEYBALL">Volleyball</option>
                <option value="NATATION">Natation</option>
                <option value="JUDO">Judo</option>
                <option value="ATHLETISME">Athlétisme</option>
                <option value="GENERAL">Général</option>
              </select>
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Date *</label>
              <input type="date" [(ngModel)]="currentMatch.date" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Heure *</label>
              <input type="time" [(ngModel)]="currentMatch.heure" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Lieu</label>
              <input type="text" [(ngModel)]="currentMatch.lieu" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Compétition</label>
              <input type="text" [(ngModel)]="currentMatch.competition" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Statut</label>
              <select [(ngModel)]="currentMatch.statut" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
                <option value="PROGRAMME">À venir</option>
                <option value="EN_COURS">En cours</option>
                <option value="TERMINE">Terminé</option>
                <option value="REPORTE">Reporté</option>
              </select>
            </div>
          </div>

          <!-- Upload image du match -->
          <div class="mb-4">
            <label class="block text-xs text-gray-400 uppercase mb-1">Image du match</label>
            <div class="flex items-center gap-4">
              <label class="cursor-pointer bg-zinc-800 border border-white/10 hover:border-wydad-red text-white px-4 py-2 rounded text-sm transition-colors">
                📷 Choisir une image
                <input type="file" accept="image/*" (change)="uploadLogo($event)" class="hidden">
              </label>
              <img *ngIf="currentMatch.imageUrl" [src]="apiService.getMediaUrl(currentMatch.imageUrl)" class="w-12 h-12 object-contain rounded border border-white/10" alt="preview">
              <span *ngIf="uploading" class="text-xs text-yellow-400 animate-pulse">Envoi en cours...</span>
            </div>
          </div>

          <div class="mt-8 flex justify-end gap-3">
            <button (click)="closeModal()" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveMatch()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Sauvegarder</button>
          </div>
        </div>
      </div>

      <!-- Modal de saisie du résultat (remplace prompt()) -->
      <div *ngIf="showScoreModal" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
        <div class="bg-zinc-900 border border-white/10 p-6 w-full max-w-md rounded-lg">
          <h3 class="text-lg font-display font-bold text-white uppercase tracking-wider mb-6">Résultat — WAC vs {{ scoreMatch?.adversaire }}</h3>
          <div class="grid grid-cols-2 gap-4 mb-6">
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Score Wydad</label>
              <input type="number" min="0" [(ngModel)]="scoreHome" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Score {{ scoreMatch?.adversaire }}</label>
              <input type="number" min="0" [(ngModel)]="scoreAway" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
          </div>
          <div class="flex justify-end gap-3">
            <button (click)="showScoreModal = false" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveScore()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Enregistrer</button>
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

  constructor(public apiService: ApiService,
              private toast: ToastService,
              private confirm: ConfirmService) {}

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
    } else {
      this.isEdit = false;
      // MatchRequest : date, heure, adversaire, competition, lieu, statut, sport
      this.currentMatch = { adversaire: '', date: '', heure: '', lieu: 'Stade Mohammed V', competition: 'Botola Pro', statut: 'PROGRAMME', sport: 'FOOTBALL', imageUrl: '' };
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
        this.currentMatch.imageUrl = res.url;
        this.uploading = false;
      },
      error: () => {
        this.uploading = false;
        this.toast.error('Erreur lors de l\'upload du logo.');
      }
    });
  }

  // Saisie du résultat via un vrai modal (remplace prompt())
  showScoreModal = false;
  scoreMatch: any = null;
  scoreHome: number | null = null;
  scoreAway: number | null = null;

  openScoreModal(match: any) {
    this.scoreMatch = match;
    this.scoreHome = match.scoreWydad ?? 0;
    this.scoreAway = match.scoreAdversaire ?? 0;
    this.showScoreModal = true;
  }

  saveScore() {
    if (this.scoreHome === null || this.scoreAway === null || !this.scoreMatch) return;
    const home = Number(this.scoreHome);
    const away = Number(this.scoreAway);
    if (!Number.isFinite(home) || !Number.isFinite(away) || home < 0 || away < 0) {
      this.toast.error('Veuillez saisir des scores valides (entiers positifs).');
      return;
    }
    // Le backend attend des query params (@RequestParam scoreWydad / scoreAdversaire)
    this.apiService.updateMatchResult(this.scoreMatch.id, home, away).subscribe({
      next: () => {
        this.toast.success('Résultat enregistré.');
        this.showScoreModal = false;
        this.loadMatchs();
      },
      error: () => this.toast.error('Erreur lors de l\'enregistrement du résultat.')
    });
  }

  closeModal() {
    this.showModal = false;
  }

  saveMatch() {
    const payload = this.payloadForBackend();
    if (this.isEdit) {
      this.apiService.updateMatch(this.currentMatch.id, payload).subscribe(() => {
        this.loadMatchs();
        this.closeModal();
      });
    } else {
      this.apiService.createMatch(payload).subscribe(() => {
        this.loadMatchs();
        this.closeModal();
      });
    }
  }

  async deleteMatch(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le match',
      message: 'Voulez-vous vraiment supprimer ce match ? Cette action est irréversible.',
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.apiService.deleteMatch(id).subscribe({
      next: () => {
        this.toast.success('Match supprimé.');
        this.loadMatchs();
      },
      error: () => this.toast.error('Erreur lors de la suppression du match.')
    });
  }

  private payloadForBackend() {
    // MatchRequest : date, heure, adversaire, competition, lieu, scoreWydad, scoreAdversaire, statut, sport
    const p: any = {
      date: this.currentMatch.date,
      // l'input time renvoie "HH:mm" ; LocalTime accepte aussi HH:mm:ss
      heure: (this.currentMatch.heure || '').length === 5 ? this.currentMatch.heure + ':00' : this.currentMatch.heure,
      adversaire: this.currentMatch.adversaire,
      competition: this.currentMatch.competition,
      lieu: this.currentMatch.lieu,
      statut: this.currentMatch.statut,
      sport: this.currentMatch.sport
    };
    if (this.currentMatch.scoreWydad != null) p.scoreWydad = this.currentMatch.scoreWydad;
    if (this.currentMatch.scoreAdversaire != null) p.scoreAdversaire = this.currentMatch.scoreAdversaire;
    return p;
  }
}
