import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Module « grand club » — Palmarès du club.
 * L'ADMIN saisit les titres affichés sur la page publique « Palmarès ».
 * Règles prouvées serveur par TrophySecurityTest : lecture publique
 * anonyme, écriture ADMIN uniquement, count >= 1.
 */
@Component({
  selector: 'app-admin-palmares',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Palmarès</h2>
        <p class="text-sm text-gray-400 mt-1">
          Titres et trophées du club affichés publiquement. Modifiable uniquement par l'ADMIN.
        </p>
      </div>

      <!-- Filtre par catégorie -->
      <div class="flex gap-3 flex-wrap items-end">
        <div class="admin-field">
          <label class="admin-label" for="tr-cat">Catégorie</label>
          <select id="tr-cat" [(ngModel)]="filterCategory" (ngModelChange)="applyFilter()" class="admin-input">
            <option value="">Toutes</option>
            <option *ngFor="let c of categories" [value]="c">{{ categoryLabels[c] || c }}</option>
          </select>
        </div>
        <div class="flex justify-end flex-1">
          <button (click)="openCreate()"
                  class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors">
            + Nouveau titre
          </button>
        </div>
      </div>

      <!-- Liste -->
      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">Titre</th>
              <th class="px-6 py-3">Catégorie</th>
              <th class="px-6 py-3">Saison</th>
              <th class="px-6 py-3 text-center">×Titres</th>
              <th class="px-6 py-3 text-center">Visible</th>
              <th class="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let t of filtered; let i = index" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4 font-medium text-white">{{ t.title }}</td>
              <td class="px-6 py-4 text-gray-300 text-sm">{{ categoryLabels[t.category] || t.category }}</td>
              <td class="px-6 py-4 text-gray-300">{{ t.season }}</td>
              <td class="px-6 py-4 text-center text-gray-200">{{ t.count }}</td>
              <td class="px-6 py-4 text-center">
                <span *ngIf="t.active" class="text-green-400 text-xs font-semibold">Oui</span>
                <span *ngIf="!t.active" class="text-gray-500 text-xs">Non</span>
              </td>
              <td class="px-6 py-4 text-right whitespace-nowrap">
                <button (click)="move(t, -1)" [disabled]="i === 0 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Monter">▲</button>
                <button (click)="move(t, 1)" [disabled]="i === filtered.length - 1 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Descendre">▼</button>
                <button (click)="openEdit(t)" class="text-blue-400 hover:text-blue-300 ml-2 text-sm">Modifier</button>
                <button (click)="remove(t)" class="text-red-400 hover:text-red-300 ml-2 text-sm">Supprimer</button>
              </td>
            </tr>
            <tr *ngIf="filtered.length === 0 && !loading">
              <td colspan="6" class="px-6 py-8 text-center text-gray-500">
                Aucun titre. Cliquez sur « Nouveau titre ».
              </td>
            </tr>
            <tr *ngIf="loading">
              <td colspan="6" class="px-6 py-8 text-center text-gray-500">Chargement...</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal création / édition -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-lg">
        <div class="admin-modal-header">
          <h3>{{ editing ? 'Modifier le titre' : 'Nouveau titre' }}</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-4">
          <p *ngIf="saveError" class="text-xs text-red-400">{{ saveError }}</p>
          <div class="admin-field">
            <label class="admin-label" for="tr-title">Intitulé<span class="req">*</span></label>
            <input id="tr-title" type="text" [(ngModel)]="form.title" class="admin-input"
                   placeholder="Ligue des Champions CAF">
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="tr-category">Catégorie<span class="req">*</span></label>
              <select id="tr-category" [(ngModel)]="form.category" class="admin-input">
                <option *ngFor="let c of categories" [value]="c">{{ categoryLabels[c] || c }}</option>
              </select>
            </div>
            <div class="admin-field">
              <label class="admin-label" for="tr-season">Saison<span class="req">*</span></label>
              <input id="tr-season" type="text" [(ngModel)]="form.season" class="admin-input"
                     placeholder="2022-2023" maxlength="20">
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="tr-count">Nombre de fois remporté<span class="req">*</span></label>
              <input id="tr-count" type="number" min="1" [(ngModel)]="form.count" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="tr-order">Ordre d'affichage</label>
              <input id="tr-order" type="number" [(ngModel)]="form.displayOrder" class="admin-input">
            </div>
          </div>
          <div class="admin-field">
            <label class="admin-label" for="tr-image">URL de l'image (logo du trophée)</label>
            <input id="tr-image" type="url" [(ngModel)]="form.imageUrl" class="admin-input"
                   placeholder="https://… (laisser vide si aucune)">
          </div>
          <div class="admin-field flex items-center gap-2">
            <input id="tr-active" type="checkbox" [(ngModel)]="form.active" class="w-4 h-4">
            <label for="tr-active" class="text-sm text-gray-300">Visible publiquement</label>
          </div>
        </div>
        <div class="admin-modal-footer">
          <span class="admin-footer-note">* Champs obligatoires — visibles immédiatement sur le site public.</span>
          <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
          <button (click)="save()" [disabled]="saving || !canSave()" class="admin-btn-primary">
            {{ saving ? 'Enregistrement...' : 'Enregistrer' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminPalmaresComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  categories = ['FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL', 'NATATION', 'JUDO', 'ATHLETISME'];
  categoryLabels: Record<string, string> = {
    FOOTBALL: 'Football', BASKETBALL: 'Basketball', HANDBALL: 'Handball',
    VOLLEYBALL: 'Volleyball', NATATION: 'Natation', JUDO: 'Judo', ATHLETISME: 'Athlétisme'
  };

  filterCategory = '';
  trophies: any[] = [];
  filtered: any[] = [];
  loading = true;
  showModal = false;
  editing = false;
  editingId: number | null = null;
  saving = false;
  busy = false;
  saveError = '';

  form = this.emptyForm();

  ngOnInit() {
    this.loadTrophies();
  }

  private emptyForm() {
    return {
      title: '', category: 'FOOTBALL', season: '', count: 1 as number,
      imageUrl: '', displayOrder: 0 as number, active: true
    };
  }

  applyFilter() {
    this.filtered = this.filterCategory
      ? this.trophies.filter(t => t.category === this.filterCategory)
      : [...this.trophies];
  }

  loadTrophies() {
    this.loading = true;
    this.api.getAllTrophies().subscribe({
      next: (list) => {
        this.trophies = list || [];
        this.applyFilter();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Impossible de charger le palmarès.');
      }
    });
  }

  openCreate() {
    this.editing = false;
    this.editingId = null;
    this.saveError = '';
    this.form = { ...this.emptyForm(), category: this.filterCategory || 'FOOTBALL' };
    this.showModal = true;
  }

  openEdit(t: any) {
    this.editing = true;
    this.editingId = t.id;
    this.saveError = '';
    this.form = {
      title: t.title, category: t.category, season: t.season, count: t.count,
      imageUrl: t.imageUrl || '', displayOrder: t.displayOrder || 0, active: !!t.active
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  canSave(): boolean {
    const f = this.form;
    return !!f.title.trim() && !!f.season.trim()
      && f.count !== null && f.count >= 1 && !!f.category;
  }

  save() {
    if (!this.canSave()) return;
    this.saving = true;
    this.saveError = '';
    const payload: any = {
      title: this.form.title.trim(),
      category: this.form.category,
      season: this.form.season.trim(),
      count: Number(this.form.count),
      imageUrl: this.form.imageUrl.trim() || null,
      displayOrder: Number(this.form.displayOrder) || 0,
      active: this.form.active
    };
    const req = this.editing && this.editingId
      ? this.api.updateTrophy(this.editingId, payload)
      : this.api.createTrophy(payload);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.showModal = false;
        this.loadTrophies();
        this.toast.success(this.editing ? 'Titre mis à jour.' : 'Titre ajouté au palmarès.');
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err.error?.message || 'Erreur lors de l\'enregistrement.';
      }
    });
  }

  remove(t: any) {
    this.confirm.confirm({
      title: 'Supprimer le titre',
      message: `« ${t.title} » sera retiré du palmarès public. Continuer ?`,
      confirmLabel: 'Supprimer',
      danger: true
    }).then(ok => {
      if (!ok) return;
      this.api.deleteTrophy(t.id).subscribe({
        next: () => {
          this.loadTrophies();
          this.toast.success('Titre supprimé.');
        },
        error: () => this.toast.error('Erreur lors de la suppression.')
      });
    });
  }

  /** Réordonne en échangeant displayOrder avec le voisin dans la liste filtrée. */
  move(t: any, dir: number) {
    const idx = this.filtered.findIndex(x => x.id === t.id);
    const target = this.filtered[idx + dir];
    if (!target || this.busy) return;
    this.busy = true;
    const tmp = t.displayOrder;
    const payloadOf = (x: any, order: number) => ({
      title: x.title, category: x.category, season: x.season, count: x.count,
      imageUrl: x.imageUrl || null, displayOrder: order, active: x.active
    });
    this.api.updateTrophy(target.id, payloadOf(target, tmp)).subscribe({
      next: () => this.api.updateTrophy(t.id, payloadOf(t, target.displayOrder)).subscribe({
        next: () => {
          this.busy = false;
          this.loadTrophies();
        },
        error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
      }),
      error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
    });
  }
}
