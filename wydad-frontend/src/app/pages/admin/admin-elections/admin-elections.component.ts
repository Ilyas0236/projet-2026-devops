import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * B.8 — Élections du président : UI ADMIN.
 *
 *  1. Ouvrir une session électorale (titre + dates).
 *  2. Ajouter les candidats (liés à un titulaire actif via <select>).
 *  3. Clôturer (gèle, ne publie PAS).
 *  4. Publier les résultats (admin) — bouton désactivé tant que
 *     participation < 100% (l'indicateur "X/Y (Z%)" l'explique).
 *
 *  Rupture B.8 par rapport à l'ancien comportement :
 *  - close() NE publie plus, il faut appeler publish() séparément.
 *  - l'admin voit la participation en temps réel (snapshot = 3 si mock,
 *    réel = titulaires ACTIVE à l'instant T).
 */
@Component({
  selector: 'app-admin-elections',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Élections du président</h2>
        <p class="text-sm text-gray-400 mt-1">
          Ouvrez une session, inscrivez les candidats (titulaires actifs uniquement),
          clôturez pour geler les votes, puis publiez les résultats définitifs
          lorsque tous les titulaires ont voté.
        </p>
      </div>

      <!-- Formulaire de création -->
      <div class="bg-white/5 border border-white/10 rounded-lg p-6 space-y-4">
        <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm">Nouvelle élection</h3>

        <input [(ngModel)]="newTitle" placeholder="Titre (ex. Élection présidentielle 2026)"
               class="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label class="text-xs text-gray-400 uppercase tracking-wider">Début du vote
            <input type="datetime-local" [(ngModel)]="newStartsAt"
                   class="mt-1 w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
          </label>
          <label class="text-xs text-gray-400 uppercase tracking-wider">Fin du vote
            <input type="datetime-local" [(ngModel)]="newEndsAt"
                   class="mt-1 w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
          </label>
        </div>

        <p *ngIf="datesError" class="text-red-300 text-xs">{{ datesError }}</p>

        <button (click)="create()" [disabled]="creating || !canCreate()"
                class="bg-wydad-red hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-white px-6 py-2.5 rounded font-bold text-sm transition-colors">
          {{ creating ? 'Création…' : 'Ouvrir la session' }}
        </button>
      </div>

      <div *ngIf="loading" class="flex justify-center py-16">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <!-- Liste des élections -->
      <div *ngIf="!loading" class="space-y-4">
        <p *ngIf="elections.length === 0" class="text-gray-500 text-sm text-center py-8">Aucune élection créée.</p>

        <div *ngFor="let e of elections" class="bg-white/5 border border-white/10 rounded-lg p-6 space-y-4">

          <div class="flex justify-between items-start gap-4 flex-wrap">
            <div>
              <h4 class="font-bold text-white">{{ e.title }}</h4>
              <p class="text-xs text-gray-400 mt-1">
                {{ e.startsAt | date:'d/MM/y HH:mm' }} → {{ e.endsAt | date:'d/MM/y HH:mm' }}
                · {{ e.totalVotes }} vote(s)
                <span [ngClass]="{
                  'text-green-300': e.status === 'CLOSED' && e.published,
                  'text-yellow-300': e.status === 'OPEN',
                  'text-blue-300': e.status === 'CLOSED' && !e.published
                }">
                  · {{ statusLabel(e) }}
                </span>
              </p>
              <p class="text-xs text-gray-400 mt-1">
                Participation :
                <span class="font-bold text-white">{{ e.totalVotes }}/{{ e.eligibleVotersCount }}</span>
                ({{ e.participationPercent }}%)
                <span *ngIf="e.closedAt">· gelée le {{ e.closedAt | date:'d/MM/y HH:mm' }}</span>
              </p>
            </div>

            <!-- B.8.b — Formulaire d'édition inline (visible si editingId === e.id) -->
            <div *ngIf="editingId === e.id" class="border-t border-white/10 pt-3 space-y-2">
              <p class="text-xs text-yellow-300 uppercase tracking-wider font-bold">Modifier l'élection</p>
              <input [(ngModel)]="editTitle[e.id]" placeholder="Titre"
                     class="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <label class="text-xs text-gray-400 uppercase tracking-wider">Début du vote
                  <input type="datetime-local" [(ngModel)]="editStartsAt[e.id]"
                         class="mt-1 w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
                </label>
                <label class="text-xs text-gray-400 uppercase tracking-wider">Fin du vote
                  <input type="datetime-local" [(ngModel)]="editEndsAt[e.id]"
                         class="mt-1 w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
                </label>
              </div>
              <p *ngIf="editError[e.id]" class="text-red-300 text-xs">{{ editError[e.id] }}</p>
              <div class="flex gap-2">
                <button (click)="saveEdit(e)" [disabled]="editing"
                        class="bg-wydad-red hover:bg-red-700 disabled:opacity-40 text-white px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                  {{ editing ? 'Enregistrement…' : 'Enregistrer' }}
                </button>
                <button (click)="cancelEdit()"
                        class="border border-white/20 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                  Annuler
                </button>
              </div>
            </div>

            <div class="flex gap-2 flex-shrink-0 flex-wrap justify-end">
              <!-- B.8 — Clôture seule (gèle, ne publie PAS) — refusée par
                   le back si moins de 2 candidats, on désactive l'UI. -->
              <button *ngIf="e.status === 'OPEN'" (click)="close(e)"
                      [disabled]="(e.candidates?.length || 0) < 2"
                      [title]="(e.candidates?.length || 0) < 2 ? 'Ajoutez au moins 2 candidats avant de geler' : 'Geler les votes (sans publier)'"
                      class="border border-white/20 hover:border-blue-400 hover:text-blue-300 disabled:opacity-40 disabled:cursor-not-allowed text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                Clôturer (geler)
              </button>

              <!-- B.8 — Publication explicite, désactivée tant que participation<100% -->
              <button *ngIf="e.status === 'CLOSED' && !e.published" (click)="publish(e)"
                      [disabled]="e.participationPercent < 100"
                      [title]="e.participationPercent < 100 ? 'Tous les titulaires doivent avoir voté' : 'Publier les résultats définitifs'"
                      class="bg-wydad-red hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-white px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                Publier ({{ e.participationPercent }}%)
              </button>

              <!-- B.8.b — Modifier (status=OPEN et 0 vote uniquement) -->
              <button *ngIf="e.status === 'OPEN' && e.totalVotes === 0 && editingId !== e.id"
                      (click)="startEdit(e)"
                      class="border border-white/20 hover:border-yellow-400 hover:text-yellow-300 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                Modifier
              </button>

              <!-- B.8.b — Dépublier (publiée uniquement) -->
              <button *ngIf="e.published" (click)="unpublish(e)"
                      class="border border-white/20 hover:border-orange-400 hover:text-orange-300 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                Dépublier
              </button>

              <!-- B.8.b — Supprimer (0 vote uniquement) -->
              <button *ngIf="e.totalVotes === 0 && editingId !== e.id"
                      (click)="delete(e)"
                      title="Supprimer l'élection (uniquement si aucun vote)"
                      class="border border-white/20 hover:border-red-400 hover:text-red-300 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                Supprimer
              </button>
            </div>
          </div>

          <!-- Candidats existants -->
          <div *ngIf="e.candidates?.length" class="space-y-2">
            <div *ngFor="let c of e.candidates"
                 class="flex items-center gap-3 bg-white/[0.03] rounded px-3 py-2">
              <img *ngIf="c.photoUrl" [src]="c.photoUrl" [alt]="'Photo de ' + c.fullName"
                   class="w-8 h-8 rounded-full object-cover">
              <div class="w-8 h-8 rounded-full bg-white/10 flex items-center justify-center text-xs font-bold text-white"
                   *ngIf="!c.photoUrl">{{ c.fullName.charAt(0) }}</div>
              <span class="text-sm text-white flex-1 truncate">
                {{ c.fullName }}
                <span *ngIf="c.userId" class="text-xs text-gray-500">(titulaire #{{ c.userId }})</span>
              </span>
              <button *ngIf="e.status === 'OPEN' && e.totalVotes === 0" (click)="removeCandidate(e, c)"
                      class="text-gray-400 hover:text-red-400 text-xs" aria-label="Retirer le candidat">✕</button>
            </div>
          </div>

          <!-- Résultats publiés -->
          <div *ngIf="e.published && e.percentages?.length" class="text-xs text-gray-400">
            Résultats : {{ candidateResultsLine(e) }}
          </div>

          <!-- Formulaire ajout candidat (élection non clôturée) -->
          <div *ngIf="e.status === 'OPEN'" class="border-t border-white/10 pt-3 space-y-2">
            <p class="text-xs text-gray-400">
              <span *ngIf="eligibleLoading">Chargement des titulaires actifs…</span>
              <span *ngIf="!eligibleLoading && eligibleMembers.length === 0" class="text-yellow-300">
                ⚠ Aucun titulaire actif pour la saison {{ currentSeason }} — impossible d'ajouter un candidat lié.
              </span>
              <span *ngIf="!eligibleLoading && eligibleMembers.length > 0">
                {{ eligibleMembers.length }} titulaire(s) actif(s) — saison {{ currentSeason }}.
              </span>
            </p>

            <div class="grid grid-cols-1 sm:grid-cols-[1fr_1fr_auto_auto] gap-2 items-end">
              <select [(ngModel)]="candUserId[e.id]"
                      [disabled]="eligibleMembers.length === 0"
                      class="bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-wydad-red">
                <option [ngValue]="undefined">— Choisir un titulaire —</option>
                <option *ngFor="let m of eligibleMembers" [ngValue]="m.id">
                  {{ m.firstName }} {{ m.lastName }} ({{ m.email }})
                </option>
              </select>
              <input [(ngModel)]="candPhoto[e.id]" placeholder="URL photo (optionnel)"
                     class="bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
              <input [(ngModel)]="candPresentation[e.id]" placeholder="Présentation courte (optionnel)"
                     class="sm:col-span-2 bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
              <button (click)="addCandidate(e)" [disabled]="!candUserId[e.id]"
                      class="bg-wydad-gold/90 hover:bg-wydad-gold disabled:opacity-40 text-black px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                + Candidat
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminElectionsComponent implements OnInit {
  api = inject(ApiService);
  toast = inject(ToastService);
  confirm = inject(ConfirmService);

  elections: any[] = [];
  loading = true;
  error = '';
  creating = false;
  datesError = '';

  newTitle = '';
  newStartsAt = '';
  newEndsAt = '';

  /** Champs du formulaire candidat, indexés par id d'élection. */
  candUserId: Record<number, number | undefined> = {};
  candPhoto: Record<number, string> = {};
  candPresentation: Record<number, string> = {};

  /** B.8.b — Édition inline d'une élection (1 seule à la fois). */
  editingId: number | null = null;
  editing = false;
  editTitle: Record<number, string> = {};
  editStartsAt: Record<number, string> = {};
  editEndsAt: Record<number, string> = {};
  editError: Record<number, string> = {};

  /** B.8 — Titulaires actifs (dropdown candidats). */
  eligibleMembers: Array<{ id: number; email: string; firstName: string; lastName: string; season: string; validTo: string; subscriptionId: number }> = [];
  eligibleLoading = false;
  /** Saison marocaine courante (août N → août N+1). */
  currentSeason = currentSeason();

  ngOnInit() {
    this.loadEligibleMembers();
    this.load();
  }

  private loadEligibleMembers() {
    this.eligibleLoading = true;
    this.api.listEligibleMembers(this.currentSeason).subscribe({
      next: (data: any[]) => {
        this.eligibleMembers = data || [];
        this.eligibleLoading = false;
      },
      error: () => {
        this.eligibleMembers = [];
        this.eligibleLoading = false;
        // On ne fait pas échouer le composant — l'admin peut quand même
        // consulter les élections existantes. Le warning visuel est dans
        // le template.
      }
    });
  }

  private load() {
    this.loading = true;
    this.api.listAllElections().subscribe({
      next: (data: any[]) => {
        this.elections = data || [];
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les élections.';
        this.loading = false;
      }
    });
  }

  canCreate(): boolean {
    if (!this.newTitle.trim() || !this.newStartsAt || !this.newEndsAt) { return false; }
    // Garde-fou : endsAt > startsAt (ne pas laisser passer une élection
    // inversée côté front, en plus de la validation serveur).
    return Date.parse(this.newStartsAt) < Date.parse(this.newEndsAt);
  }

  create() {
    if (!this.canCreate()) {
      this.datesError = 'La date de fin doit être postérieure à la date de début.';
      return;
    }
    this.datesError = '';
    this.creating = true;
    this.api.createElection(this.newTitle.trim(), toIso(this.newStartsAt), toIso(this.newEndsAt))
      .subscribe({
        next: () => {
          this.toast.success('Session électorale ouverte.');
          this.newTitle = ''; this.newStartsAt = ''; this.newEndsAt = '';
          this.creating = false;
          this.load();
        },
        error: (err: any) => {
          this.creating = false;
          this.toast.error(err?.error?.message || 'Création impossible.');
        }
      });
  }

  addCandidate(e: any) {
    const userId = this.candUserId[e.id];
    if (!userId) { return; }
    const member = this.eligibleMembers.find(m => m.id === userId);
    const fullName = member ? `${member.firstName} ${member.lastName}` : '';
    this.api.addElectionCandidate(e.id, fullName,
        (this.candPresentation[e.id] || '').trim() || undefined,
        (this.candPhoto[e.id] || '').trim() || undefined,
        userId)
      .subscribe({
        next: () => {
          this.toast.success('Candidat ajouté.');
          this.candUserId[e.id] = undefined;
          this.candPhoto[e.id] = '';
          this.candPresentation[e.id] = '';
          this.load();
        },
        error: (err: any) => {
          this.toast.error(err?.error?.message || 'Ajout impossible.');
        }
      });
  }

  async removeCandidate(e: any, candidate: any) {
    const ok = await this.confirm.confirm({
      title: `Retirer ${candidate.fullName} ?`,
      message: 'Cette action est définitive.',
      confirmLabel: 'Retirer',
      danger: true
    });
    if (!ok) { return; }
    this.api.removeElectionCandidate(e.id, candidate.id).subscribe({
      next: () => {
        this.toast.success('Candidat retiré.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Suppression impossible.');
      }
    });
  }

  close(e: any) {
    this.api.closeElection(e.id).subscribe({
      next: () => {
        this.toast.success('Élection gelée — les votes sont figés. Cliquez sur Publier pour diffuser les résultats.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Clôture impossible.');
      }
    });
  }

  publish(e: any) {
    this.api.publishElection(e.id).subscribe({
      next: () => {
        this.toast.success('Résultats publiés sur la page publique.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Publication impossible.');
      }
    });
  }

  statusLabel(e: any): string {
    if (e.published) { return 'Publiée'; }
    if (e.status === 'CLOSED') { return 'Gelée (à publier)'; }
    return 'Ouverte';
  }

  candidateResultsLine(e: any): string {
    return (e.candidates || [])
      .map((c: any, i: number) => `${c.fullName} ${e.percentages[i]}% (${e.results[i]})`)
      .join(' · ');
  }

  /** B.8.b — Ouvre le formulaire d'édition inline pour une élection. */
  startEdit(e: any) {
    this.editingId = e.id;
    this.editError[e.id] = '';
    // datetime-local attend du "YYYY-MM-DDTHH:mm" ; on tronque les
    // secondes de l'ISO reçu du back.
    this.editTitle[e.id] = e.title;
    this.editStartsAt[e.id] = (e.startsAt || '').substring(0, 16);
    this.editEndsAt[e.id] = (e.endsAt || '').substring(0, 16);
  }

  cancelEdit() {
    this.editingId = null;
  }

  saveEdit(e: any) {
    this.editError[e.id] = '';
    const title = (this.editTitle[e.id] || '').trim();
    const startsAt = this.editStartsAt[e.id];
    const endsAt = this.editEndsAt[e.id];
    if (!title) { this.editError[e.id] = 'Titre obligatoire.'; return; }
    if (!startsAt || !endsAt) { this.editError[e.id] = 'Dates obligatoires.'; return; }
    if (Date.parse(startsAt) >= Date.parse(endsAt)) {
      this.editError[e.id] = 'La date de fin doit être postérieure à la date de début.';
      return;
    }
    this.editing = true;
    this.api.updateElection(e.id, title, toIso(startsAt), toIso(endsAt))
      .subscribe({
        next: () => {
          this.toast.success('Élection modifiée.');
          this.editing = false;
          this.editingId = null;
          this.load();
        },
        error: (err: any) => {
          this.editing = false;
          this.editError[e.id] = err?.error?.message || 'Modification impossible.';
        }
      });
  }

  /** B.8.b — Dépublier (annule un publishResults). */
  async unpublish(e: any) {
    const ok = await this.confirm.confirm({
      title: 'Dépublier cette élection ?',
      message: `Les résultats de « ${e.title} » ne seront plus visibles publiquement.`,
      confirmLabel: 'Dépublier',
      danger: true
    });
    if (!ok) { return; }
    this.api.unpublishElection(e.id).subscribe({
      next: () => {
        this.toast.success('Élection dépubliée.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Dépublication impossible.');
      }
    });
  }

  /** B.8.b — Supprimer une élection (uniquement si 0 vote). */
  async delete(e: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer cette élection ?',
      message: `« ${e.title} » sera supprimée définitivement avec ses candidats. Action irréversible.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) { return; }
    this.api.deleteElection(e.id).subscribe({
      next: () => {
        this.toast.success('Élection supprimée.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Suppression impossible.');
      }
    });
  }
}

/** datetime-local -> ISO avec secondes (le backend attend LocalDateTime). */
function toIso(localValue: string): string {
  return localValue.length === 16 ? `${localValue}:00` : localValue;
}

/**
 * Saison sportive marocaine : commence en août.
 * Si on est en août-décembre (mois ≥ 8) → "YYYY-(YYYY+1)".
 * Si on est en janvier-juillet → "(YYYY-1)-YYYY".
 * Exemples : 02/09/2026 → "2026-2027", 15/03/2026 → "2025-2026".
 */
function currentSeason(now: Date = new Date()): string {
  const y = now.getFullYear();
  const m = now.getMonth() + 1; // 1-12
  if (m >= 8) { return `${y}-${y + 1}`; }
  return `${y - 1}-${y}`;
}
