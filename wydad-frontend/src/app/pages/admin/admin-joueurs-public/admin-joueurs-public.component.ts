import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Fonctionnalité 6/6 — Fiches joueurs PUBLIQUES (page Effectif du site).
 * L'ADMIN saisit ici les stats affichées au public : matchs joués, buts,
 * passes. Règles prouvées côté serveur par JoueurSecurityTest (lecture
 * publique anonyme ; écriture ADMIN uniquement).
 */
@Component({
  selector: 'app-admin-joueurs-public',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Effectif Public</h2>
        <p class="text-sm text-gray-400 mt-1">
          Fiches et statistiques affichées sur la page « Effectif » publique. Modifiable uniquement par l'ADMIN.
        </p>
      </div>

      <!-- Filtre par section sportive -->
      <div class="flex gap-3 flex-wrap items-end">
        <div class="admin-field">
          <label class="admin-label" for="jp-sport">Section</label>
          <select id="jp-sport" [(ngModel)]="filterSport" (ngModelChange)="loadJoueurs()" class="admin-input">
            <option *ngFor="let s of sports" [value]="s">{{ sportLabels[s] || s }}</option>
          </select>
        </div>
        <div class="flex justify-end flex-1">
          <button (click)="openCreate()"
                  class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors">
            + Nouvelle fiche
          </button>
        </div>
      </div>

      <!-- Liste -->
      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">N°</th>
              <th class="px-6 py-3">Joueur</th>
              <th class="px-6 py-3">Poste</th>
              <th class="px-6 py-3 text-center">Matchs</th>
              <th class="px-6 py-3 text-center">{{ getGoalLabel() }}</th>
              <th class="px-6 py-3 text-center">Passes</th>
              <th class="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let j of joueurs; let i = index" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4 text-gray-300">{{ j.numero }}</td>
              <td class="px-6 py-4">
                <span class="font-medium text-white">{{ j.nom }}</span>
                <span class="block text-xs text-gray-500">{{ j.age }} ans</span>
              </td>
              <td class="px-6 py-4 text-gray-300 text-sm">{{ j.poste }}</td>
              <td class="px-6 py-4 text-center text-gray-200">{{ j.matchsJoues || 0 }}</td>
              <td class="px-6 py-4 text-center text-gray-200">{{ j.buts || 0 }}</td>
              <td class="px-6 py-4 text-center text-gray-200">{{ j.passes || 0 }}</td>
              <td class="px-6 py-4 text-right whitespace-nowrap">
                <button (click)="move(j, -1)" [disabled]="i === 0 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Monter">▲</button>
                <button (click)="move(j, 1)" [disabled]="i === joueurs.length - 1 || busy"
                        class="text-gray-400 hover:text-white px-1 disabled:opacity-30" title="Descendre">▼</button>
                <button (click)="openEdit(j)" class="text-blue-400 hover:text-blue-300 ml-2 text-sm">Modifier</button>
                <button (click)="remove(j)" class="text-red-400 hover:text-red-300 ml-2 text-sm">Supprimer</button>
              </td>
            </tr>
            <tr *ngIf="joueurs.length === 0 && !loading">
              <td colspan="7" class="px-6 py-8 text-center text-gray-500">
                Aucune fiche pour cette section. Cliquez sur « Nouvelle fiche ».
              </td>
            </tr>
            <tr *ngIf="loading">
              <td colspan="7" class="px-6 py-8 text-center text-gray-500">Chargement...</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal création / édition -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-lg">
        <div class="admin-modal-header">
          <h3>{{ editing ? 'Modifier la fiche' : 'Nouvelle fiche joueur' }}</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-4">
          <p *ngIf="saveError" class="text-xs text-red-400">{{ saveError }}</p>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="jp-nom">Nom<span class="req">*</span></label>
              <input id="jp-nom" type="text" [(ngModel)]="form.nom" class="admin-input" placeholder="Nom du joueur">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="jp-poste">Poste<span class="req">*</span></label>
              <input id="jp-poste" type="text" [(ngModel)]="form.poste" class="admin-input" placeholder="Attaquant">
            </div>
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="jp-age">Âge<span class="req">*</span></label>
              <input id="jp-age" type="number" min="10" max="60" [(ngModel)]="form.age" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="jp-numero">Numéro<span class="req">*</span></label>
              <input id="jp-numero" type="number" min="1" max="99" [(ngModel)]="form.numero" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="jp-sport-form">Section<span class="req">*</span></label>
              <select id="jp-sport-form" [(ngModel)]="form.sport" class="admin-input">
                <option *ngFor="let s of sports" [value]="s">{{ sportLabels[s] || s }}</option>
              </select>
            </div>
          </div>
          <div class="admin-field">
            <label class="admin-label" for="jp-photo">URL de la photo</label>
            <input id="jp-photo" type="url" [(ngModel)]="form.photoUrl" class="admin-input"
                   placeholder="https://… (laisser vide si aucune)">
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="jp-matchs">Matchs joués</label>
              <input id="jp-matchs" type="number" min="0" [(ngModel)]="form.matchsJoues" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="jp-buts">{{ getGoalLabel() }}</label>
              <input id="jp-buts" type="number" min="0" [(ngModel)]="form.buts" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="jp-passes">Passes</label>
              <input id="jp-passes" type="number" min="0" [(ngModel)]="form.passes" class="admin-input">
            </div>
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
export class AdminJoueursPublicComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  sports = ['FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL', 'NATATION', 'JUDO', 'ATHLETISME'];
  sportLabels: Record<string, string> = {
    FOOTBALL: 'Football', BASKETBALL: 'Basketball', HANDBALL: 'Handball',
    VOLLEYBALL: 'Volleyball', NATATION: 'Natation', JUDO: 'Judo', ATHLETISME: 'Athlétisme'
  };

  filterSport = 'FOOTBALL';
  joueurs: any[] = [];
  loading = true;
  showModal = false;
  editing = false;
  editingId: number | null = null;
  saving = false;
  busy = false;
  saveError = '';

  form = this.emptyForm();

  ngOnInit() {
    this.loadJoueurs();
  }

  private emptyForm() {
    return {
      nom: '', poste: '', age: null as number | null, numero: null as number | null,
      sport: 'FOOTBALL', photoUrl: '', matchsJoues: 0, buts: 0, passes: 0
    };
  }

  getGoalLabel(): string {
    return this.filterSport === 'BASKETBALL' ? 'Points' : 'Buts';
  }

  loadJoueurs() {
    this.loading = true;
    this.api.getJoueursBySport(this.filterSport).subscribe({
      next: (list) => {
        // Tri serveur absent : numéro croissant comme la page publique
        this.joueurs = (list || []).sort((a: any, b: any) => (a.numero || 99) - (b.numero || 99));
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Impossible de charger les fiches.');
      }
    });
  }

  openCreate() {
    this.editing = false;
    this.editingId = null;
    this.saveError = '';
    this.form = { ...this.emptyForm(), sport: this.filterSport };
    this.showModal = true;
  }

  openEdit(j: any) {
    this.editing = true;
    this.editingId = j.id;
    this.saveError = '';
    this.form = {
      nom: j.nom, poste: j.poste, age: j.age, numero: j.numero,
      sport: j.sport, photoUrl: j.photoUrl || '',
      matchsJoues: j.matchsJoues || 0, buts: j.buts || 0, passes: j.passes || 0
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  canSave(): boolean {
    const f = this.form;
    return !!f.nom.trim() && !!f.poste.trim()
      && f.age !== null && f.numero !== null && !!f.sport;
  }

  save() {
    if (!this.canSave()) return;
    this.saving = true;
    this.saveError = '';
    const payload = {
      nom: this.form.nom.trim(),
      poste: this.form.poste.trim(),
      age: Number(this.form.age),
      numero: Number(this.form.numero),
      sport: this.form.sport,
      photoUrl: this.form.photoUrl.trim() || null,
      matchsJoues: Number(this.form.matchsJoues) || 0,
      buts: Number(this.form.buts) || 0,
      passes: Number(this.form.passes) || 0
    };
    const req = this.editing && this.editingId
      ? this.api.updateJoueur(this.editingId, payload)
      : this.api.createJoueur(payload);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.showModal = false;
        this.loadJoueurs();
        this.toast.success(this.editing ? 'Fiche mise à jour.' : 'Fiche créée.');
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err.error?.message || 'Erreur lors de l\'enregistrement.';
      }
    });
  }

  remove(j: any) {
    this.confirm.confirm({
      title: 'Supprimer la fiche',
      message: `La fiche de ${j.nom} ne sera plus visible publiquement. Continuer ?`,
      confirmLabel: 'Supprimer',
      danger: true
    }).then(ok => {
      if (!ok) return;
      this.api.deleteJoueur(j.id).subscribe({
        next: () => {
          this.loadJoueurs();
          this.toast.success('Fiche supprimée.');
        },
        error: () => this.toast.error('Erreur lors de la suppression.')
      });
    });
  }

  /**
   * Réordonne en échangeant les numéros d'affichage avec le voisin.
   * Le tri public se fait sur le numéro : échanger les numéros change l'ordre.
   */
  move(j: any, dir: number) {
    const idx = this.joueurs.findIndex(x => x.id === j.id);
    const target = this.joueurs[idx + dir];
    if (!target || this.busy) return;
    const tmp = j.numero;
    this.busy = true;
    const payloadOf = (x: any, numero: number) => ({
      nom: x.nom, poste: x.poste, age: x.age, numero,
      sport: x.sport, photoUrl: x.photoUrl || null,
      matchsJoues: x.matchsJoues || 0, buts: x.buts || 0, passes: x.passes || 0
    });
    this.api.updateJoueur(target.id, payloadOf(target, tmp)).subscribe({
      next: () => this.api.updateJoueur(j.id, payloadOf(j, target.numero)).subscribe({
        next: () => {
          this.busy = false;
          this.loadJoueurs();
        },
        error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
      }),
      error: () => { this.busy = false; this.toast.error('Erreur lors du réordonnancement.'); }
    });
  }
}
