import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

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
          <option value="Botola Pro">Botola Pro</option>
          <option value="CAF Champions League">CAF Champions League</option>
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
            <tr *ngFor="let item of classements" class="hover:bg-white/5 transition-colors" [ngClass]="{'bg-wydad-red/10': item.teamName.includes('Wydad')}">
              <td class="py-3 px-4 text-sm text-white font-bold">{{ item.position }}</td>
              <td class="py-3 px-4 text-sm text-white font-medium">{{ item.teamName }}</td>
              <td class="py-3 px-4 text-sm text-white font-bold text-center">{{ item.points }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.played }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.won }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.drawn }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.lost }}</td>
              <td class="py-3 px-4 text-sm text-gray-400 text-center">{{ item.goalsFor - item.goalsAgainst }}</td>
              <td class="py-3 px-4 text-right">
                <button (click)="openModal(item)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteClassement(item.id)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal -->
      <div *ngIf="showModal" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
        <div class="bg-zinc-900 border border-white/10 p-6 w-full max-w-lg rounded-lg">
          <h3 class="text-xl font-display font-bold text-white uppercase tracking-wider mb-6">
            {{ isEdit ? 'Modifier Ligne' : 'Nouvelle Ligne' }}
          </h3>
          
          <div class="grid grid-cols-2 gap-4 mb-4">
            <div class="col-span-2">
              <label class="block text-xs text-gray-400 uppercase mb-1">Équipe</label>
              <input type="text" [(ngModel)]="currentClassement.teamName" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Position</label>
              <input type="number" [(ngModel)]="currentClassement.position" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Points</label>
              <input type="number" [(ngModel)]="currentClassement.points" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Joués</label>
              <input type="number" [(ngModel)]="currentClassement.played" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Gagnés</label>
              <input type="number" [(ngModel)]="currentClassement.won" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Nuls</label>
              <input type="number" [(ngModel)]="currentClassement.drawn" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Perdus</label>
              <input type="number" [(ngModel)]="currentClassement.lost" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Buts +</label>
              <input type="number" [(ngModel)]="currentClassement.goalsFor" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Buts -</label>
              <input type="number" [(ngModel)]="currentClassement.goalsAgainst" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
          </div>
          
          <div class="mt-8 flex justify-end gap-3">
            <button (click)="closeModal()" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveClassement()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Sauvegarder</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminClassementsComponent implements OnInit {
  classements: any[] = [];
  loading = true;
  selectedCompetition = 'Botola Pro';
  showModal = false;
  isEdit = false;
  currentClassement: any = {};

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadClassements();
  }

  loadClassements() {
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
      this.currentClassement = { competition: this.selectedCompetition, teamName: '', position: 1, points: 0, played: 0, won: 0, drawn: 0, lost: 0, goalsFor: 0, goalsAgainst: 0 };
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

  deleteClassement(id: number) {
    if (confirm('Voulez-vous vraiment supprimer cette équipe du classement ?')) {
      this.apiService.deleteClassement(id).subscribe(() => this.loadClassements());
    }
  }
}
