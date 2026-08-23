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
      <div *ngIf="showModal" class="admin-overlay">
        <div class="admin-modal max-w-2xl">
          <div class="admin-modal-header">
            <h3>{{ isEdit ? 'Modifier le Match' : 'Nouveau Match' }}</h3>
            <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>

          <div class="admin-modal-body space-y-5">
            <div class="grid grid-cols-2 gap-4">
              <div class="admin-field">
                <label class="admin-label">Adversaire<span class="req">*</span></label>
                <input type="text" [(ngModel)]="currentMatch.adversaire" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Sport<span class="req">*</span></label>
                <select [(ngModel)]="currentMatch.sport" class="admin-input">
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
              <div class="admin-field">
                <label class="admin-label">Date<span class="req">*</span></label>
                <input type="date" [(ngModel)]="currentMatch.date" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Heure<span class="req">*</span></label>
                <input type="time" [(ngModel)]="currentMatch.heure" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Lieu</label>
                <input type="text" [(ngModel)]="currentMatch.lieu" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Compétition</label>
                <select [(ngModel)]="currentMatch.competition" class="admin-input">
                  <option *ngFor="let c of competitions" [value]="c.name">{{ c.name }}</option>
                </select>
              </div>
              <div class="admin-field">
                <label class="admin-label">Statut</label>
                <select [(ngModel)]="currentMatch.statut" class="admin-input">
                  <option value="PROGRAMME">À venir</option>
                  <option value="EN_COURS">En cours</option>
                  <option value="TERMINE">Terminé</option>
                  <option value="REPORTE">Reporté</option>
                </select>
              </div>
            </div>

            <!-- Upload image du match -->
            <div class="admin-field">
              <label class="admin-label">Image du match</label>
              <div class="admin-upload-zone">
                <label class="admin-upload-btn">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                  Choisir une image
                  <input type="file" accept="image/*" (change)="uploadLogo($event)">
                </label>
                <img *ngIf="currentMatch.imageUrl" [src]="apiService.getMediaUrl(currentMatch.imageUrl)" class="admin-upload-preview w-12 h-12 object-contain" alt="preview">
                <span *ngIf="uploading" class="admin-upload-status">Envoi en cours...</span>
              </div>
            </div>
          </div>

          <div class="admin-modal-footer">
            <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
            <button (click)="saveMatch()" class="admin-btn-primary">Sauvegarder</button>
          </div>
        </div>
      </div>

      <!-- Modal de saisie du résultat (remplace prompt()) -->
      <div *ngIf="showScoreModal" class="admin-overlay">
        <div class="admin-modal max-w-md">
          <div class="admin-modal-header">
            <h3>Résultat — WAC vs {{ scoreMatch?.adversaire }}</h3>
          </div>
          <div class="admin-modal-body">
            <div class="grid grid-cols-2 gap-4">
              <div class="admin-field">
                <label class="admin-label">Score Wydad</label>
                <input type="number" min="0" [(ngModel)]="scoreHome" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Score {{ scoreMatch?.adversaire }}</label>
                <input type="number" min="0" [(ngModel)]="scoreAway" class="admin-input">
              </div>
            </div>
          </div>
          <div class="admin-modal-footer">
            <button (click)="showScoreModal = false" class="admin-btn-ghost">Annuler</button>
            <button (click)="saveScore()" class="admin-btn-primary">Enregistrer</button>
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

  competitions: any[] = [];

  constructor(public apiService: ApiService,
              private toast: ToastService,
              private confirm: ConfirmService) {}

  ngOnInit() {
    // Competitions dynamiques : parametre club 'competitions' (source ADMIN)
    this.apiService.getCompetitions().subscribe({
      next: (data) => this.competitions = Array.isArray(data) ? data : [],
      error: () => this.competitions = []
    });
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
      const comp = this.competitions.find(c => c.sport === 'FOOTBALL');
      this.currentMatch = { adversaire: '', date: '', heure: '', lieu: 'Stade Mohammed V', competition: comp?.name || '', statut: 'PROGRAMME', sport: 'FOOTBALL', imageUrl: '' };
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
