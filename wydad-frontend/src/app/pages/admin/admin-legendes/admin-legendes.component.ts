import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Module « grand club » — Légendes du club (Hall of Fame).
 * Règles prouvées serveur par ClubLegendSecurityTest : lecture publique
 * anonyme, écriture ADMIN uniquement, années 1900..courante cohérentes.
 */
@Component({
  selector: 'app-admin-legendes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Légendes</h2>
        <p class="text-sm text-gray-400 mt-1">
          Hall of Fame du club affiché publiquement. Modifiable uniquement par l'ADMIN.
        </p>
      </div>

      <div class="flex justify-end">
        <button (click)="openCreate()"
                class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors">
          + Nouvelle légende
        </button>
      </div>

      <!-- Liste -->
      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">Légende</th>
              <th class="px-6 py-3">Poste</th>
              <th class="px-6 py-3 text-center">Période</th>
              <th class="px-6 py-3 text-center">Visible</th>
              <th class="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let l of legends; let i = index" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4">
                <span class="font-medium text-white">{{ l.name }}</span>
                <span *ngIf="l.nickname" class="block text-xs text-gray-500">« {{ l.nickname }} »</span>
              </td>
              <td class="px-6 py-4 text-gray-300 text-sm">{{ l.role }}</td>
              <td class="px-6 py-4 text-center text-gray-200">{{ periode(l) }}</td>
              <td class="px-6 py-4 text-center">
                <span *ngIf="l.active" class="text-green-400 text-xs font-semibold">Oui</span>
                <span *ngIf="!l.active" class="text-gray-500 text-xs">Non</span>
              </td>
              <td class="px-6 py-4 text-right whitespace-nowrap">
                <button (click)="move(l, -1)" [disabled]="i === 0 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Monter">▲</button>
                <button (click)="move(l, 1)" [disabled]="i === legends.length - 1 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Descendre">▼</button>
                <button (click)="openEdit(l)" class="text-blue-400 hover:text-blue-300 ml-2 text-sm">Modifier</button>
                <button (click)="remove(l)" class="text-red-400 hover:text-red-300 ml-2 text-sm">Supprimer</button>
              </td>
            </tr>
            <tr *ngIf="legends.length === 0 && !loading">
              <td colspan="5" class="px-6 py-8 text-center text-gray-500">
                Aucune légende. Cliquez sur « Nouvelle légende ».
              </td>
            </tr>
            <tr *ngIf="loading">
              <td colspan="5" class="px-6 py-8 text-center text-gray-500">Chargement...</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal création / édition -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-lg">
        <div class="admin-modal-header">
          <h3>{{ editing ? 'Modifier la légende' : 'Nouvelle légende' }}</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-4">
          <p *ngIf="saveError" class="text-xs text-red-400">{{ saveError }}</p>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="lg-name">Nom<span class="req">*</span></label>
              <input id="lg-name" type="text" [(ngModel)]="form.name" class="admin-input"
                     placeholder="Mustapha Bettache">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="lg-nick">Surnom</label>
              <input id="lg-nick" type="text" [(ngModel)]="form.nickname" class="admin-input" placeholder="Betta">
            </div>
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="lg-role">Poste / Discipline<span class="req">*</span></label>
              <input id="lg-role" type="text" [(ngModel)]="form.role" class="admin-input" placeholder="Attaquant">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="lg-from">Année début<span class="req">*</span></label>
              <input id="lg-from" type="number" min="1900" [max]="maxYear" [(ngModel)]="form.yearFrom" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="lg-to">Année fin</label>
              <input id="lg-to" type="number" min="1900" [max]="maxYear" [(ngModel)]="form.yearTo" class="admin-input"
                     placeholder="(optionnel)">
            </div>
          </div>
          <div class="admin-field">
            <label class="admin-label" for="lg-bio">Biographie courte</label>
            <textarea id="lg-bio" rows="3" [(ngModel)]="form.biography" class="admin-input"
                      placeholder="Quelques lignes sur son parcours au club…"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="lg-photo">URL de la photo</label>
              <input id="lg-photo" type="url" [(ngModel)]="form.imageUrl" class="admin-input"
                     placeholder="https://… (laisser vide si aucune)">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="lg-order">Ordre d'affichage</label>
              <input id="lg-order" type="number" [(ngModel)]="form.displayOrder" class="admin-input">
            </div>
          </div>
          <div class="admin-field flex items-center gap-2">
            <input id="lg-active" type="checkbox" [(ngModel)]="form.active" class="w-4 h-4">
            <label for="lg-active" class="text-sm text-gray-300">Visible publiquement</label>
          </div>
        </div>
        <div class="admin-modal-footer">
          <span class="admin-footer-note">* Champs obligatoires — années entre 1900 et {{ maxYear }}.</span>
          <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
          <button (click)="save()" [disabled]="saving || !canSave()" class="admin-btn-primary">
            {{ saving ? 'Enregistrement...' : 'Enregistrer' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminLegendesComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  /** Même borne haute que la validation serveur (ClubLegendService). */
  maxYear = new Date().getFullYear();

  legends: any[] = [];
  loading = true;
  showModal = false;
  editing = false;
  editingId: number | null = null;
  saving = false;
  busy = false;
  saveError = '';

  form = this.emptyForm();

  ngOnInit() {
    this.loadLegends();
  }

  private emptyForm() {
    return {
      name: '', nickname: '', role: '', yearFrom: null as number | null,
      yearTo: null as number | null, biography: '', imageUrl: '',
      displayOrder: 0 as number, active: true
    };
  }

  periode(l: any): string {
    return l.yearTo ? `${l.yearFrom} – ${l.yearTo}` : `${l.yearFrom} – …`;
  }

  loadLegends() {
    this.loading = true;
    this.api.getAllLegends().subscribe({
      next: (list) => {
        this.legends = list || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Impossible de charger les légendes.');
      }
    });
  }

  openCreate() {
    this.editing = false;
    this.editingId = null;
    this.saveError = '';
    this.form = this.emptyForm();
    this.showModal = true;
  }

  openEdit(l: any) {
    this.editing = true;
    this.editingId = l.id;
    this.saveError = '';
    this.form = {
      name: l.name, nickname: l.nickname || '', role: l.role,
      yearFrom: l.yearFrom, yearTo: l.yearTo ?? null,
      biography: l.biography || '', imageUrl: l.imageUrl || '',
      displayOrder: l.displayOrder || 0, active: !!l.active
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  canSave(): boolean {
    const f = this.form;
    return !!f.name.trim() && !!f.role.trim()
      && f.yearFrom !== null && f.yearFrom >= 1900 && f.yearFrom <= this.maxYear
      && (f.yearTo === null || f.yearTo >= (f.yearFrom as number));
  }

  save() {
    if (!this.canSave()) return;
    this.saving = true;
    this.saveError = '';
    const payload: any = {
      name: this.form.name.trim(),
      nickname: this.form.nickname.trim() || null,
      role: this.form.role.trim(),
      yearFrom: Number(this.form.yearFrom),
      yearTo: this.form.yearTo === null ? null : Number(this.form.yearTo),
      biography: this.form.biography.trim() || null,
      imageUrl: this.form.imageUrl.trim() || null,
      displayOrder: Number(this.form.displayOrder) || 0,
      active: this.form.active
    };
    const req = this.editing && this.editingId
      ? this.api.updateLegend(this.editingId, payload)
      : this.api.createLegend(payload);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.showModal = false;
        this.loadLegends();
        this.toast.success(this.editing ? 'Légende mise à jour.' : 'Légende ajoutée.');
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err.error?.message || 'Erreur lors de l\'enregistrement.';
      }
    });
  }

  remove(l: any) {
    this.confirm.confirm({
      title: 'Supprimer la légende',
      message: `« ${l.name} » sera retirée du Hall of Fame public. Continuer ?`,
      confirmLabel: 'Supprimer',
      danger: true
    }).then(ok => {
      if (!ok) return;
      this.api.deleteLegend(l.id).subscribe({
        next: () => {
          this.loadLegends();
          this.toast.success('Légende supprimée.');
        },
        error: () => this.toast.error('Erreur lors de la suppression.')
      });
    });
  }

  /** Réordonne en échangeant displayOrder avec le voisin. */
  move(l: any, dir: number) {
    const idx = this.legends.findIndex(x => x.id === l.id);
    const target = this.legends[idx + dir];
    if (!target || this.busy) return;
    this.busy = true;
    const tmp = l.displayOrder;
    const payloadOf = (x: any, order: number) => ({
      name: x.name, nickname: x.nickname || null, role: x.role,
      yearFrom: x.yearFrom, yearTo: x.yearTo ?? null,
      biography: x.biography || null, imageUrl: x.imageUrl || null,
      displayOrder: order, active: x.active
    });
    this.api.updateLegend(target.id, payloadOf(target, tmp)).subscribe({
      next: () => this.api.updateLegend(l.id, payloadOf(l, target.displayOrder)).subscribe({
        next: () => {
          this.busy = false;
          this.loadLegends();
        },
        error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
      }),
      error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
    });
  }
}
