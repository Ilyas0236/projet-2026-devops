import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * B.10 — Traitement des réclamations des membres (ADMIN).
 * Répondre enregistre la réponse officielle + statut ; le plaignant est
 * notifié côté serveur.
 */
@Component({
  selector: 'app-admin-reclamations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Réclamations</h2>
        <p class="text-sm text-gray-400 mt-1">Traitement des réclamations soumises par les membres via l'onglet Support de leur profil.</p>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading && !error">
        <!-- Filtre par statut -->
        <div class="flex gap-2 mb-4 flex-wrap">
          <button *ngFor="let f of filters"
                  (click)="activeFilter = f.value; page = 1"
                  class="px-4 py-1.5 rounded-full text-xs uppercase font-bold tracking-wider border transition-colors"
                  [ngClass]="activeFilter === f.value
                    ? {'bg-wydad-red': true, 'text-white': true, 'border-wydad-red': true}
                    : {'bg-transparent': true, 'text-gray-400': true, 'border-white/10': true, 'hover:text-white': true}">
            {{ f.label }} ({{ countByStatus(f.value) }})
          </button>
        </div>

        <div *ngIf="filtered().length === 0" class="text-sm text-gray-500 py-8 text-center">Aucune réclamation pour ce filtre.</div>

        <div *ngFor="let r of paged()" class="bg-white/5 border border-white/10 rounded-lg p-5 mb-3">
          <div class="flex flex-col md:flex-row md:items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap mb-1">
                <span class="font-bold text-white text-sm">{{ r.title }}</span>
                <span class="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-white/10 text-gray-300">{{ r.subject }}</span>
                <span class="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full font-bold"
                      [ngClass]="statusChipClass(r.status)">{{ statusLabel(r.status) }}</span>
              </div>
              <p class="text-xs text-gray-500 mb-2">
                Membre #{{ r.userId }} · {{ r.userEmail }} · {{ r.createdAt | date:'dd/MM/yyyy HH:mm' }}
              </p>
              <p class="text-sm text-gray-300 whitespace-pre-line">{{ r.description }}</p>
            </div>

            <!-- Formulaire de réponse -->
            <div class="w-full md:w-80 shrink-0 space-y-2 pt-2 md:pt-0">
              <div *ngIf="r.adminResponse" class="text-[11px] bg-green-500/10 border border-green-500/20 text-green-300 rounded p-2 whitespace-pre-line">
                <span class="font-bold uppercase tracking-wider">Réponse actuelle :</span> {{ r.adminResponse }}
              </div>
              <textarea [(ngModel)]="responses[r.id]" rows="3" name="resp-{{ r.id }}"
                        placeholder="Votre réponse officielle…"
                        class="admin-input !text-sm w-full"></textarea>
              <div class="flex gap-2">
                <select [(ngModel)]="statuses[r.id]" name="stat-{{ r.id }}" class="admin-input !text-sm flex-1">
                  <option value="IN_PROGRESS">En cours</option>
                  <option value="RESOLVED">Résolue</option>
                  <option value="REJECTED">Rejetée</option>
                  <option value="OPEN">Réouvrir</option>
                </select>
                <button (click)="respond(r)"
                        [disabled]="!(responses[r.id] || '').trim()"
                        class="px-4 py-2 bg-wydad-gold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-yellow-600 text-black uppercase text-xs font-bold tracking-wider shrink-0">
                  Répondre
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Pagination simple -->
        <div *ngIf="totalPages() > 1" class="flex justify-center gap-2 mt-6">
          <button (click)="page = page - 1" [disabled]="page <= 1"
                  class="px-3 py-1.5 text-xs uppercase font-bold rounded bg-white/10 text-gray-300 disabled:opacity-40">Précédent</button>
          <span class="px-3 py-1.5 text-xs text-gray-500">Page {{ page }} / {{ totalPages() }}</span>
          <button (click)="page = page + 1" [disabled]="page >= totalPages()"
                  class="px-3 py-1.5 text-xs uppercase font-bold rounded bg-white/10 text-gray-300 disabled:opacity-40">Suivant</button>
        </div>
      </div>
    </div>
  `
})
export class AdminReclamationsComponent implements OnInit {
  reclamations: any[] = [];
  loading = true;
  error = '';

  // brouillons de réponse / statut, indexés par id de réclamation
  responses: Record<number, string> = {};
  statuses: Record<number, string> = {};

  filters = [
    { value: 'ALL', label: 'Toutes' },
    { value: 'OPEN', label: 'Ouvertes' },
    { value: 'IN_PROGRESS', label: 'En cours' },
    { value: 'RESOLVED', label: 'Résolues' },
    { value: 'REJECTED', label: 'Rejetées' }
  ];
  activeFilter = 'ALL';
  page = 1;
  readonly pageSize = 10;

  constructor(private apiService: ApiService, private toast: ToastService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.error = '';
    this.apiService.getAllReclamations().subscribe({
      next: (list) => {
        this.reclamations = list || [];
        this.loading = false;
      },
      error: () => {
        this.error = "Impossible de charger les réclamations.";
        this.loading = false;
      }
    });
  }

  filtered(): any[] {
    return this.activeFilter === 'ALL'
      ? this.reclamations
      : this.reclamations.filter((r) => r.status === this.activeFilter);
  }

  paged(): any[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  }

  totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered().length / this.pageSize));
  }

  countByStatus(status: string): number {
    return status === 'ALL'
      ? this.reclamations.length
      : this.reclamations.filter((r) => r.status === status).length;
  }

  respond(r: any) {
    const response = (this.responses[r.id] || '').trim();
    if (!response) return;
    this.apiService.respondReclamation(r.id, response, this.statuses[r.id] || 'RESOLVED').subscribe({
      next: () => {
        this.toast.success('Réponse enregistrée — le membre est notifié.');
        delete this.responses[r.id];
        this.load();
      },
      error: () => this.toast.error("Échec de l'enregistrement de la réponse.")
    });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'OPEN': return 'Ouverte';
      case 'IN_PROGRESS': return 'En cours';
      case 'RESOLVED': return 'Résolue';
      case 'REJECTED': return 'Rejetée';
      default: return status;
    }
  }

  statusChipClass(status: string): any {
    switch (status) {
      case 'OPEN': return {'bg-yellow-500/15': true, 'text-yellow-400': true};
      case 'IN_PROGRESS': return {'bg-blue-500/15': true, 'text-blue-300': true};
      case 'RESOLVED': return {'bg-green-500/15': true, 'text-green-400': true};
      case 'REJECTED': return {'bg-red-500/15': true, 'text-red-400': true};
      default: return {'bg-white/10': true, 'text-gray-300': true};
    }
  }
}
