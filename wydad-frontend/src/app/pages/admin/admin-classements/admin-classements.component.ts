import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-classements',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Gestion des Classements</h2>
          <p class="text-sm text-gray-400 mt-1">Mettez à jour les classements par compétition.</p>
        </div>
        <button (click)="openModal()" class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 font-display font-bold uppercase tracking-wider text-sm skew-x-[-10deg] transition-colors">
          <span class="skew-x-[10deg] block">Nouvelle Ligne</span>
        </button>
      </div>

      <div class="bg-white/5 border border-white/10 rounded-lg p-4">
        <label class="block text-xs text-gray-400 uppercase tracking-wider mb-2">Compétition</label>
        <select [(ngModel)]="selectedCompetition" (ngModelChange)="loadClassements()" class="w-64 bg-black border border-white/10 rounded px-3 py-2 text-sm text-white focus:border-wydad-red focus:outline-none">
          <option *ngFor="let c of competitions" [value]="c.name">{{ c.name }}</option>
        </select>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="!loading" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="py-3 px-4">Pos</th>
              <th class="py-3 px-4">Équipe</th>
              <th class="py-3 px-4 text-center">PTS</th>
              <th class="py-3 px-4 text-center">J</th>
              <th class="py-3 px-4 text-center">G</th>
              <th class="py-3 px-4 text-center">N</th>
              <th class="py-3 px-4 text-center">P</th>
              <th class="py-3 px-4 text-center">DIFF</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let item of classements" class="hover:bg-white/5 transition-colors" [ngClass]="{'bg-wydad-red/10': item.equipe.includes('Wydad')}">
              <td class="py-3 px-4 text-sm text-white font-bold">{{ item.position }}</td>
              <td class="py-3 px-4 text-sm text-white font-medium">{{ item.equipe }}</td>
              <td class="py-3 px-4 text-sm text-white font-bold text-center">{{ item.points }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.joues }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.gagnes }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.nuls }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.perdus }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.bp - item.bc }}</td>
              <td class="py-3 px-4 text-right">
                <button (click)="openModal(item)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteClassement(item.id)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal -->
      <div *ngIf="showModal" class="admin-overlay">
        <div class="admin-modal max-w-lg">
          <div class="admin-modal-header">
            <h3>{{ isEdit ? 'Modifier Ligne' : 'Nouvelle Ligne' }}</h3>
            <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>

          <div class="admin-modal-body">
            <div class="grid grid-cols-2 gap-4">
              <div class="admin-field col-span-2">
                <label class="admin-label">Équipe<span class="req">*</span></label>
                <input type="text" [(ngModel)]="currentClassement.equipe" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Position</label>
                <input type="number" min="1" [(ngModel)]="currentClassement.position" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Points</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.points" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Joués</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.joues" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Gagnés</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.gagnes" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Nuls</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.nuls" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Perdus</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.perdus" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Buts +</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.bp" class="admin-input">
              </div>
              <div class="admin-field">
                <label class="admin-label">Buts -</label>
                <input type="number" min="0" [(ngModel)]="currentClassement.bc" class="admin-input">
              </div>
            </div>
          </div>

          <div class="admin-modal-footer">
            <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
            <button (click)="saveClassement()" class="admin-btn-primary">Sauvegarder</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminClassementsComponent implements OnInit {
  classements: any[] = [];
  competitions: any[] = [];
  loading = true;
  selectedCompetition = '';
  showModal = false;
  isEdit = false;
  currentClassement: any = {};

  constructor(private apiService: ApiService,
              private toast: ToastService,
              private confirm: ConfirmService) {}

  ngOnInit() {
    // Competitions dynamiques : parametre club 'competitions' (source ADMIN)
    this.apiService.getCompetitions().subscribe({
      next: (data) => {
        this.competitions = Array.isArray(data) ? data : [];
        if (!this.selectedCompetition && this.competitions.length > 0) {
          this.selectedCompetition = this.competitions[0].name;
          this.loadClassements();
        }
      },
      error: (err) => {
        console.error(err);
        this.toast.error('Impossible de charger les compétitions.');
        this.loading = false;
      }
    });
  }

  loadClassements() {
    if (!this.selectedCompetition) return;
    this.loading = true;
    this.apiService.getClassements(this.selectedCompetition).subscribe({
      next: (data) => {
        this.classements = data.sort((a, b) => a.position - b.position);
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });
  }

  openModal(item?: any) {
    if (item) {
      this.isEdit = true;
      this.currentClassement = { ...item };
    } else {
      this.isEdit = false;
      const comp = this.competitions.find(c => c.name === this.selectedCompetition);
      this.currentClassement = { competition: this.selectedCompetition, sport: comp?.sport || 'FOOTBALL', equipe: '', position: 1, points: 0, joues: 0, gagnes: 0, nuls: 0, perdus: 0, bp: 0, bc: 0 };
    }
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  saveClassement() {
    if (this.isEdit) {
      this.apiService.updateClassement(this.currentClassement.id, this.currentClassement).subscribe(() => {
        this.loadClassements();
        this.closeModal();
      });
    } else {
      this.currentClassement.competition = this.selectedCompetition;
      this.apiService.createClassement(this.currentClassement).subscribe(() => {
        this.loadClassements();
        this.closeModal();
      });
    }
  }

  async deleteClassement(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer l\'équipe',
      message: 'Voulez-vous vraiment supprimer cette équipe du classement ?',
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.apiService.deleteClassement(id).subscribe({
      next: () => this.loadClassements(),
      error: () => this.toast.error('Erreur lors de la suppression.')
    });
  }
}
