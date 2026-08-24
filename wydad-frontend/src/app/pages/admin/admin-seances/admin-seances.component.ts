import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * Fonctionnalité 3/6 — Planning des entraînements côté ADMIN.
 * L'ADMIN crée des séances pour n'importe quel sport/catégorie (le STAFF
 * passe par son dashboard). Chaque joueur du groupe visé reçoit une
 * notification IN_APP — règle prouvée côté serveur par SessionSecurityTest.
 */
@Component({
  selector: 'app-admin-seances',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Séances d'Entraînement</h2>
        <p class="text-sm text-gray-400 mt-1">
          Planifiez les séances par sport et catégorie. Chaque joueur du groupe visé est notifié automatiquement.
        </p>
      </div>

      <!-- Filtre -->
      <div class="flex gap-3 flex-wrap items-end">
        <div class="admin-field">
          <label class="admin-label" for="filtre-sport">Sport</label>
          <select id="filtre-sport" [(ngModel)]="filterSport" (ngModelChange)="loadSessions()" class="admin-input">
            <option *ngFor="let s of sports" [value]="s">{{ sportLabels[s] || s }}</option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" for="filtre-cat">Catégorie</label>
          <select id="filtre-cat" [(ngModel)]="filterCategory" (ngModelChange)="loadSessions()" class="admin-input">
            <option *ngFor="let c of categories" [value]="c">{{ c }}</option>
          </select>
        </div>
      </div>

      <div class="flex justify-end">
        <button (click)="openModal()"
                class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm flex items-center gap-2 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          Nouvelle séance
        </button>
      </div>

      <!-- Liste -->
      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">Séance</th>
              <th class="px-6 py-3">Date</th>
              <th class="px-6 py-3">Lieu</th>
              <th class="px-6 py-3">Groupe</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let s of sessions" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4">
                <span class="font-medium text-white">{{ s.title }}</span>
                <span *ngIf="s.description" class="block text-xs text-gray-500">{{ s.description }}</span>
              </td>
              <td class="px-6 py-4 text-gray-300 text-sm whitespace-nowrap">{{ s.sessionDate | date:'dd/MM/yyyy à HH:mm' }}</td>
              <td class="px-6 py-4 text-gray-300 text-sm">{{ s.location || '—' }}</td>
              <td class="px-6 py-4">
                <span class="px-2 py-1 rounded text-xs font-medium bg-wydad-red/20 text-red-300 whitespace-nowrap">
                  {{ sportLabels[s.sportType] || s.sportType }} · {{ s.category }}
                </span>
              </td>
            </tr>
            <tr *ngIf="sessions.length === 0 && !loading">
              <td colspan="4" class="px-6 py-8 text-center text-gray-500">
                Aucune séance pour ce groupe. Cliquez sur « Nouvelle séance ».
              </td>
            </tr>
            <tr *ngIf="loading">
              <td colspan="4" class="px-6 py-8 text-center text-gray-500">Chargement...</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal création -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-lg">
        <div class="admin-modal-header">
          <h3>Nouvelle séance d'entraînement</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-5">
          <div class="admin-field">
            <label class="admin-label" for="se-titre">Titre<span class="req">*</span></label>
            <input id="se-titre" type="text" [(ngModel)]="form.title" class="admin-input"
                   placeholder="Entraînement technique">
          </div>
          <div class="admin-field">
            <label class="admin-label" for="se-desc">Description</label>
            <textarea id="se-desc" rows="2" [(ngModel)]="form.description" class="admin-input"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="se-date">Date & heure<span class="req">*</span></label>
              <input id="se-date" type="datetime-local" [(ngModel)]="form.sessionDate" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="se-lieu">Lieu</label>
              <input id="se-lieu" type="text" [(ngModel)]="form.location" class="admin-input"
                     placeholder="Stade Mohammed V">
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="se-sport">Sport<span class="req">*</span></label>
              <select id="se-sport" [(ngModel)]="form.sportType" class="admin-input">
                <option *ngFor="let s of sports" [value]="s">{{ sportLabels[s] || s }}</option>
              </select>
            </div>
            <div class="admin-field">
              <label class="admin-label" for="se-cat">Catégorie<span class="req">*</span></label>
              <select id="se-cat" [(ngModel)]="form.category" class="admin-input">
                <option *ngFor="let c of categories" [value]="c">{{ c }}</option>
              </select>
            </div>
          </div>
          <p class="text-xs text-gray-500">
            Tous les joueurs du groupe sélectionné recevront une notification.
          </p>
        </div>
        <div class="admin-modal-footer">
          <span class="admin-footer-note">* Champs obligatoires</span>
          <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
          <button (click)="save()" [disabled]="saving || !canSave()" class="admin-btn-primary">
            {{ saving ? 'Création...' : 'Créer la séance' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminSeancesComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);

  sports = ['FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL', 'SWIMMING', 'JUDO', 'ATHLETICS'];
  categories = ['PRO', 'ESPOIR', 'U19', 'U17', 'U15', 'ACADEMY'];
  sportLabels: Record<string, string> = {
    FOOTBALL: 'Football', BASKETBALL: 'Basketball', HANDBALL: 'Handball',
    VOLLEYBALL: 'Volleyball', SWIMMING: 'Natation', JUDO: 'Judo',
    ATHLETICS: 'Athlétisme'
  };

  filterSport = 'FOOTBALL';
  filterCategory = 'PRO';

  sessions: any[] = [];
  loading = true;
  showModal = false;
  saving = false;

  form = this.emptyForm();

  ngOnInit() {
    this.loadSessions();
  }

  private emptyForm() {
    return {
      title: '',
      description: '',
      location: '',
      sessionDate: '',
      sportType: 'FOOTBALL',
      category: 'PRO'
    };
  }

  loadSessions() {
    this.loading = true;
    this.api.getSessionsByCategory(this.filterSport, this.filterCategory).subscribe({
      next: (list) => {
        this.sessions = list || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement séances', err);
        this.loading = false;
        this.toast.error('Impossible de charger les séances.');
      }
    });
  }

  openModal() {
    // Pré-remplir avec le groupe consulté
    this.form = { ...this.emptyForm(), sportType: this.filterSport, category: this.filterCategory };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  canSave(): boolean {
    return this.form.title.trim().length > 0 && this.form.sessionDate.length > 0;
  }

  save() {
    if (!this.canSave()) return;
    const payload = {
      title: this.form.title.trim(),
      description: this.form.description || null,
      location: this.form.location || null,
      sessionDate: new Date(this.form.sessionDate).toISOString(),
      sportType: this.form.sportType,
      category: this.form.category,
      // Requis par le contrat DTO ; l'ADMIN agit hors périmètre staff
      // catégorie — le backend accepte 0 pour une action administrative.
      createdByStaffId: 0
    };

    this.saving = true;
    this.api.createSession(payload).subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.loadSessions();
        this.toast.success('Séance créée. Les joueurs du groupe sont notifiés.');
      },
      error: (err) => {
        console.error('Erreur création séance', err);
        this.saving = false;
        this.toast.error(err.error?.message || 'Erreur lors de la création de la séance.');
      }
    });
  }
}
