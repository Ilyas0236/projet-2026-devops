import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Fonctionnalité 2/6 — Badges & défis pilotés par l'ADMIN.
 * Le catalogue est entièrement gérable ici (création, édition du seuil,
 * activation/désactivation, suppression). L'attribution reste 100 %
 * automatique côté serveur : dès que le solde de points d'un membre
 * atteint le seuil d'un badge actif, le badge lui est attribué — aucune
 * route d'attribution manuelle n'existe (prouvé par les tests B.8).
 */
@Component({
  selector: 'app-admin-badges',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Badges & Défis</h2>
        <p class="text-sm text-gray-400 mt-1">
          Le catalogue est attribué automatiquement : un membre débloque un badge quand son solde de points
          atteint le seuil. Points gagnés via pronostics, achats boutique/billetterie et bonus.
        </p>
      </div>

      <!-- Sous-onglets (Commit 1 gamification) : Catalogue | Attributions récentes -->
      <div class="flex gap-2 border-b border-white/10">
        <button (click)="setTab('catalog')"
                class="px-4 py-2 text-sm font-bold uppercase tracking-wider transition-colors border-b-2"
                [ngClass]="tab === 'catalog' ? 'border-wydad-red text-white' : 'border-transparent text-gray-400 hover:text-white'">
          Catalogue
        </button>
        <button (click)="setTab('history')"
                class="px-4 py-2 text-sm font-bold uppercase tracking-wider transition-colors border-b-2"
                [ngClass]="tab === 'history' ? 'border-wydad-red text-white' : 'border-transparent text-gray-400 hover:text-white'">
          Attributions récentes
        </button>
      </div>

      <!-- ============ Catalogue ============ -->
      <div *ngIf="tab === 'catalog'" class="space-y-6">
        <div class="flex justify-end">
          <button (click)="openModal()"
                  class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm flex items-center gap-2 transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
            Nouveau badge
          </button>
        </div>

        <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
          <table class="w-full text-left border-collapse">
            <thead>
              <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
                <th class="px-6 py-3">Badge</th>
                <th class="px-6 py-3">Code</th>
                <th class="px-6 py-3">Seuil (points)</th>
                <th class="px-6 py-3">État</th>
                <th class="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              <tr *ngFor="let badge of badges" class="hover:bg-white/5 transition-colors">
                <td class="px-6 py-4">
                  <span class="font-medium text-white">{{ badge.name }}</span>
                  <span *ngIf="badge.description" class="block text-xs text-gray-500">{{ badge.description }}</span>
                </td>
                <td class="px-6 py-4 font-mono text-gray-300 text-sm">{{ badge.code }}</td>
                <td class="px-6 py-4 text-white font-bold">{{ badge.minPoints }}</td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 rounded text-xs font-medium"
                        [ngClass]="badge.active ? 'bg-green-500/20 text-green-400' : 'bg-gray-500/20 text-gray-400'">
                    {{ badge.active ? 'Actif' : 'Inactif' }}
                  </span>
                </td>
                <td class="px-6 py-4 text-right whitespace-nowrap">
                  <button (click)="toggle(badge)" class="uppercase text-xs font-bold mr-3 transition-colors"
                          [class]="badge.active ? 'text-yellow-400 hover:text-yellow-300' : 'text-green-400 hover:text-green-300'">
                    {{ badge.active ? 'Désactiver' : 'Réactiver' }}
                  </button>
                  <button (click)="edit(badge)" class="text-blue-400 hover:text-blue-300 uppercase text-xs font-bold mr-3 transition-colors">Éditer</button>
                  <button (click)="remove(badge)" class="text-red-400 hover:text-red-300 uppercase text-xs font-bold transition-colors">Supprimer</button>
                </td>
              </tr>
              <tr *ngIf="badges.length === 0 && !loading">
                <td colspan="5" class="px-6 py-8 text-center text-gray-500">
                  Aucun badge. Cliquez sur « Nouveau badge » pour créer le premier palier de fidélité.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ============ Attributions récentes ============ -->
      <div *ngIf="tab === 'history'" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <!-- Bloc attributions de badges -->
          <div class="bg-white/5 border border-white/10 rounded-lg p-5">
            <h3 class="font-display font-bold text-lg text-white flex items-center gap-2 mb-3">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-yellow-400" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
              Badges débloqués récemment
            </h3>
            <p class="text-xs text-gray-400 mb-4">Attributions 100 % automatiques — aucun bouton « attribuer » n'existe.</p>
            <div *ngIf="loadingHistory" class="text-gray-500 text-sm py-4">Chargement...</div>
            <div *ngIf="!loadingHistory && recentAwards.length === 0" class="text-gray-500 text-sm py-4 text-center">
              Aucun badge débloqué pour le moment.
            </div>
            <div class="divide-y divide-white/5 max-h-[28rem] overflow-y-auto">
              <div *ngFor="let a of recentAwards" class="py-3 flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <p class="text-sm text-white font-medium truncate">{{ a.badgeName }} <span class="text-gray-500 text-xs">({{ a.badgeCode }})</span></p>
                  <p class="text-[11px] text-gray-500">Utilisateur #{{ a.userId }} • seuil {{ a.minPoints }} pts</p>
                </div>
                <span class="text-[11px] text-gray-400 flex-shrink-0">{{ a.awardedAt | date:'dd/MM HH:mm' }}</span>
              </div>
            </div>
          </div>

          <!-- Bloc flux global de points -->
          <div class="bg-white/5 border border-white/10 rounded-lg p-5">
            <h3 class="font-display font-bold text-lg text-white flex items-center gap-2 mb-3">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1"/></svg>
              Flux de points
            </h3>
            <p class="text-xs text-gray-400 mb-4">Toutes les opérations (pronostics, achats, bonus) — piste d'audit.</p>
            <div *ngIf="loadingHistory" class="text-gray-500 text-sm py-4">Chargement...</div>
            <div *ngIf="!loadingHistory && recentTransactions.length === 0" class="text-gray-500 text-sm py-4 text-center">
              Aucune transaction enregistrée.
            </div>
            <div class="divide-y divide-white/5 max-h-[28rem] overflow-y-auto">
              <div *ngFor="let tx of recentTransactions" class="py-3 flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <p class="text-sm text-white font-medium truncate">{{ tx.label || tx.source }}</p>
                  <p class="text-[11px] text-gray-500">User #{{ tx.userId }} • {{ tx.source }} • {{ tx.createdAt | date:'dd/MM HH:mm' }}</p>
                </div>
                <span class="font-display font-bold flex-shrink-0"
                      [ngClass]="tx.amount > 0 ? 'text-emerald-400' : 'text-red-400'">
                  {{ tx.amount > 0 ? '+' : '' }}{{ tx.amount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal création / édition -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-md">
        <div class="admin-modal-header">
          <h3>{{ editingId ? 'Modifier le badge' : 'Nouveau badge' }}</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-5">
          <div class="admin-field" *ngIf="!editingId">
            <label class="admin-label" for="badge-code">Code<span class="req">*</span></label>
            <input id="badge-code" type="text" [(ngModel)]="form.code" class="admin-input uppercase"
                   placeholder="FIDELE" [disabled]="editingId !== null">
          </div>
          <div class="admin-field">
            <label class="admin-label" for="badge-name">Nom<span class="req">*</span></label>
            <input id="badge-name" type="text" [(ngModel)]="form.name" class="admin-input" placeholder="Fidèle">
          </div>
          <div class="admin-field">
            <label class="admin-label" for="badge-desc">Description</label>
            <textarea id="badge-desc" rows="2" [(ngModel)]="form.description" class="admin-input"
                      placeholder="Attribué après…"></textarea>
          </div>
          <div class="admin-field">
            <label class="admin-label" for="badge-min">Seuil de points<span class="req">*</span></label>
            <input id="badge-min" type="number" min="0" [(ngModel)]="form.minPoints" class="admin-input">
          </div>
        </div>
        <div class="admin-modal-footer">
          <span class="admin-footer-note">Attribution automatique dès le seuil atteint.</span>
          <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
          <button (click)="save()" [disabled]="saving || !canSave()" class="admin-btn-primary">
            {{ saving ? 'Enregistrement...' : 'Sauvegarder' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminBadgesComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  badges: any[] = [];
  loading = true;
  showModal = false;
  saving = false;
  editingId: number | null = null;

  /** Sous-onglet actif (Commit 1 gamification). */
  tab: 'catalog' | 'history' = 'catalog';
  /** Onglet « Attributions récentes » — données chargées à la demande. */
  recentAwards: any[] = [];
  recentTransactions: any[] = [];
  loadingHistory = false;

  form = this.emptyForm();

  ngOnInit() {
    this.load();
  }

  setTab(t: 'catalog' | 'history') {
    this.tab = t;
    if (t === 'history' && this.recentAwards.length === 0 && this.recentTransactions.length === 0 && !this.loadingHistory) {
      this.loadHistory();
    }
  }

  loadHistory() {
    this.loadingHistory = true;
    this.api.getRecentBadgeAwards(50).subscribe({
      next: (data) => (this.recentAwards = data || []),
      error: (err) => console.error('Erreur attributions récentes', err)
    });
    this.api.getRecentPointTransactions(100).subscribe({
      next: (data) => {
        this.recentTransactions = data || [];
        this.loadingHistory = false;
      },
      error: (err) => {
        console.error('Erreur transactions récentes', err);
        this.loadingHistory = false;
      }
    });
  }

  private emptyForm() {
    return { code: '', name: '', description: '', minPoints: 100 };
  }

  load() {
    // /badges/all : l'ADMIN voit aussi les badges désactivés
    this.loading = true;
    this.api.getAllBadges().subscribe({
      next: (list) => {
        this.badges = list || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement badges', err);
        this.loading = false;
        this.toast.error('Impossible de charger les badges.');
      }
    });
  }

  canSave(): boolean {
    return this.form.name.trim().length > 0
      && this.form.minPoints !== null && this.form.minPoints >= 0
      && (this.editingId !== null || this.form.code.trim().length > 0);
  }

  openModal() {
    this.editingId = null;
    this.form = this.emptyForm();
    this.showModal = true;
  }

  edit(badge: any) {
    this.editingId = badge.id;
    this.form = {
      code: badge.code,
      name: badge.name,
      description: badge.description || '',
      minPoints: badge.minPoints
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  save() {
    if (!this.canSave()) return;
    const payload: any = {
      name: this.form.name.trim(),
      description: this.form.description || null,
      minPoints: this.form.minPoints,
      active: true
    };
    if (this.editingId === null) payload.code = this.form.code.trim();

    this.saving = true;
    const call$ = this.editingId !== null
      ? this.api.updateBadge(this.editingId, payload)
      : this.api.createBadge(payload);

    call$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.load();
        this.toast.success(this.editingId !== null ? 'Badge mis à jour.' : 'Badge créé.');
      },
      error: (err) => {
        console.error('Erreur sauvegarde badge', err);
        this.saving = false;
        this.toast.error(err.error?.message || 'Erreur lors de la sauvegarde du badge.');
      }
    });
  }

  async toggle(badge: any) {
    const target = !badge.active;
    const ok = await this.confirm.confirm({
      title: target ? 'Réactiver le badge' : 'Désactiver le badge',
      message: target
        ? `Le badge « ${badge.name} » redevient attribuable automatiquement.`
        : `Le badge « ${badge.name} » ne sera plus attribué (les membres qui le possèdent le gardent).`,
      confirmLabel: target ? 'Réactiver' : 'Désactiver',
      danger: !target
    });
    if (!ok) return;
    this.api.updateBadge(badge.id, { active: target }).subscribe({
      next: () => this.load(),
      error: (err) => {
        console.error('Erreur activation badge', err);
        this.toast.error(err.error?.message || 'Erreur lors de la modification.');
      }
    });
  }

  async remove(badge: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le badge',
      message: `Supprimer définitivement « ${badge.name} » ? Les membres qui le possèdent perdront ce badge.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteBadge(badge.id).subscribe({
      next: () => {
        this.toast.success('Badge supprimé.');
        this.load();
      },
      error: (err) => {
        console.error('Erreur suppression badge', err);
        this.toast.error(err.error?.message || 'Erreur lors de la suppression.');
      }
    });
  }
}
