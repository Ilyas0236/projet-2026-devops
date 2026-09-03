import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * B.8.c — Pilotage admin des défis temporels.
 *
 * CRUD complet (création, édition, désactivation, suppression) + bouton
 * "Évaluer maintenant" qui force le passage de la fonction d'évaluation
 * (utile quand l'admin ferme la fenêtre manuellement).
 *
 * Le barème de récompenses (CHALLENGE) est crédité via addPoints → le
 * solde utilisateur est mis à jour, l'historique (point_transactions) est
 * tracé, et les badges franchis sont attribués automatiquement.
 */
@Component({
  selector: 'app-admin-defis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Défis temporels</h2>
        <p class="text-sm text-gray-400 mt-1">
          Définissez des objectifs limités dans le temps (pronostics, scores exacts, palier de points).
          La récompense est créditée automatiquement dès que l'utilisateur atteint la cible.
        </p>
      </div>

      <div class="flex flex-wrap gap-3 justify-end">
        <button (click)="evaluateNow()"
                class="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-medium text-sm flex items-center gap-2 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
          Évaluer maintenant
        </button>
        <button (click)="openModal()"
                class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 rounded-lg font-medium text-sm flex items-center gap-2 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          Nouveau défi
        </button>
      </div>

      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">Titre</th>
              <th class="px-6 py-3">Type</th>
              <th class="px-6 py-3">Cible</th>
              <th class="px-6 py-3">Récompense</th>
              <th class="px-6 py-3">Fenêtre</th>
              <th class="px-6 py-3">État</th>
              <th class="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let c of challenges" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4">
                <span class="font-medium text-white">{{ c.title }}</span>
                <span *ngIf="c.description" class="block text-xs text-gray-500">{{ c.description }}</span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-300">{{ challengeTypeLabel(c.challengeType) }}</td>
              <td class="px-6 py-4 text-white font-bold">{{ c.target }}</td>
              <td class="px-6 py-4 text-emerald-400 font-bold">+{{ c.rewardPoints }} pts</td>
              <td class="px-6 py-4 text-xs text-gray-400">
                {{ c.startsAt | date:'dd/MM/yy' }} → {{ c.endsAt | date:'dd/MM/yy' }}
              </td>
              <td class="px-6 py-4">
                <span class="px-2 py-1 rounded text-xs font-medium"
                      [ngClass]="c.active ? 'bg-green-500/20 text-green-400' : 'bg-gray-500/20 text-gray-400'">
                  {{ c.active ? 'Actif' : 'Inactif' }}
                </span>
              </td>
              <td class="px-6 py-4 text-right whitespace-nowrap">
                <button (click)="edit(c)" class="text-blue-400 hover:text-blue-300 uppercase text-xs font-bold mr-3 transition-colors">Éditer</button>
                <button (click)="remove(c)" class="text-red-400 hover:text-red-300 uppercase text-xs font-bold transition-colors">Supprimer</button>
              </td>
            </tr>
            <tr *ngIf="challenges.length === 0 && !loading">
              <td colspan="7" class="px-6 py-8 text-center text-gray-500">
                Aucun défi. Cliquez sur « Nouveau défi » pour en créer un.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal création / édition -->
    <div *ngIf="showModal" class="admin-overlay">
      <div class="admin-modal max-w-lg">
        <div class="admin-modal-header">
          <h3>{{ editingId ? 'Modifier le défi' : 'Nouveau défi' }}</h3>
          <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">✕</button>
        </div>
        <div class="admin-modal-body space-y-4">
          <div class="admin-field">
            <label class="admin-label" for="chg-title">Titre<span class="req">*</span></label>
            <input id="chg-title" type="text" [(ngModel)]="form.title" class="admin-input"
                   placeholder="Pronostiqueur du mois">
          </div>
          <div class="admin-field">
            <label class="admin-label" for="chg-desc">Description</label>
            <textarea id="chg-desc" rows="2" [(ngModel)]="form.description" class="admin-input"
                      placeholder="Soumettez 5 pronostics pendant le mois de septembre"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="chg-type">Type<span class="req">*</span></label>
              <select id="chg-type" [(ngModel)]="form.challengeType" class="admin-input">
                <option value="SUBMIT_PREDICTIONS">Soumettre des pronostics</option>
                <option value="WIN_EXACT_SCORES">Scores exacts validés</option>
                <option value="REACH_POINTS">Atteindre un solde de points</option>
              </select>
            </div>
            <div class="admin-field">
              <label class="admin-label" for="chg-active">État</label>
              <select id="chg-active" [(ngModel)]="form.active" class="admin-input">
                <option [ngValue]="true">Actif</option>
                <option [ngValue]="false">Inactif</option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="chg-target">Cible<span class="req">*</span></label>
              <input id="chg-target" type="number" min="1" [(ngModel)]="form.target" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="chg-reward">Récompense (pts)<span class="req">*</span></label>
              <input id="chg-reward" type="number" min="1" [(ngModel)]="form.rewardPoints" class="admin-input">
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="admin-field">
              <label class="admin-label" for="chg-start">Début<span class="req">*</span></label>
              <input id="chg-start" type="datetime-local" [(ngModel)]="form.startsAt" class="admin-input">
            </div>
            <div class="admin-field">
              <label class="admin-label" for="chg-end">Fin<span class="req">*</span></label>
              <input id="chg-end" type="datetime-local" [(ngModel)]="form.endsAt" class="admin-input">
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <span class="admin-footer-note">Récompense créditée automatiquement à la cible.</span>
          <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
          <button (click)="save()" [disabled]="saving || !canSave()" class="admin-btn-primary">
            {{ saving ? 'Enregistrement...' : 'Sauvegarder' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminDefisComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  challenges: any[] = [];
  loading = true;
  showModal = false;
  saving = false;
  editingId: number | null = null;

  form = this.emptyForm();

  ngOnInit() {
    this.load();
  }

  private emptyForm() {
    const now = new Date();
    const inAMonth = new Date(now.getTime() + 30 * 24 * 3600 * 1000);
    return {
      title: '',
      description: '',
      challengeType: 'SUBMIT_PREDICTIONS',
      target: 5,
      rewardPoints: 50,
      startsAt: this.toLocalInput(now),
      endsAt: this.toLocalInput(inAMonth),
      active: true
    };
  }

  /** Format "yyyy-MM-ddTHH:mm" pour <input type="datetime-local">. */
  private toLocalInput(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  load() {
    this.loading = true;
    this.api.getAllChallenges().subscribe({
      next: (list) => {
        this.challenges = list || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement défis', err);
        this.loading = false;
        this.toast.error('Impossible de charger les défis.');
      }
    });
  }

  canSave(): boolean {
    const f = this.form;
    return f.title.trim().length > 0
      && f.target > 0
      && f.rewardPoints > 0
      && !!f.startsAt
      && !!f.endsAt;
  }

  openModal() {
    this.editingId = null;
    this.form = this.emptyForm();
    this.showModal = true;
  }

  edit(c: any) {
    this.editingId = c.id;
    this.form = {
      title: c.title,
      description: c.description || '',
      challengeType: c.challengeType,
      target: c.target,
      rewardPoints: c.rewardPoints,
      startsAt: this.toLocalInput(new Date(c.startsAt)),
      endsAt: this.toLocalInput(new Date(c.endsAt)),
      active: c.active
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  save() {
    if (!this.canSave()) return;
    const payload: any = {
      title: this.form.title.trim(),
      description: this.form.description || null,
      challengeType: this.form.challengeType,
      target: this.form.target,
      rewardPoints: this.form.rewardPoints,
      startsAt: new Date(this.form.startsAt).toISOString(),
      endsAt: new Date(this.form.endsAt).toISOString(),
      active: this.form.active
    };

    this.saving = true;
    const call$ = this.editingId !== null
      ? this.api.updateChallenge(this.editingId, payload)
      : this.api.createChallenge(payload);

    call$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.load();
        this.toast.success(this.editingId !== null ? 'Défi mis à jour.' : 'Défi créé.');
      },
      error: (err) => {
        console.error('Erreur sauvegarde défi', err);
        this.saving = false;
        this.toast.error(err.error?.message || 'Erreur lors de la sauvegarde du défi.');
      }
    });
  }

  async remove(c: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le défi',
      message: `Supprimer définitivement « ${c.title} » ? Les progressions des utilisateurs seront perdues.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteChallenge(c.id).subscribe({
      next: () => {
        this.toast.success('Défi supprimé.');
        this.load();
      },
      error: (err) => {
        console.error('Erreur suppression défi', err);
        this.toast.error(err.error?.message || 'Erreur lors de la suppression.');
      }
    });
  }

  evaluateNow() {
    this.api.evaluateChallenges().subscribe({
      next: (res) => {
        this.toast.success(`Évaluation terminée. ${res.touched} récompense(s) créditée(s).`);
        this.load();
      },
      error: (err) => {
        console.error('Erreur évaluation', err);
        this.toast.error(err.error?.message || 'Erreur lors de l\'évaluation.');
      }
    });
  }

  challengeTypeLabel(type: string): string {
    switch (type) {
      case 'SUBMIT_PREDICTIONS': return 'Soumettre des pronostics';
      case 'WIN_EXACT_SCORES': return 'Scores exacts';
      case 'REACH_POINTS': return 'Palier de points';
      default: return type;
    }
  }
}
