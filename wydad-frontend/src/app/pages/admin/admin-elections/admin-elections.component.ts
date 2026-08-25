import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * B.8 — Élections du président : UI ADMIN.
 * 1. Ouvrir une session électorale (titre + dates).
 * 2. Ajouter les candidats (nom, photo URL, présentation).
 * 3. Clôturer : les résultats (gagnant + %) sont calculés côté serveur
 *    et publiés automatiquement sur la page publique.
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
          Ouvrez une session, inscrivez les candidats, puis clôturez : gagnant et pourcentages sont
          calculés côté serveur et publiés sur la page publique.
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
                <span [ngClass]="e.published ? 'text-green-300' : 'text-yellow-300'">
                  · {{ e.published ? 'Publiée' : 'Ouverte' }}
                </span>
              </p>
            </div>
            <button *ngIf="!e.published" (click)="close(e)"
                    class="flex-shrink-0 border border-white/20 hover:border-red-400 hover:text-red-300 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
              Clôturer &amp; publier
            </button>
          </div>

          <!-- Candidats existants -->
          <div *ngIf="e.candidates?.length" class="space-y-2">
            <div *ngFor="let c of e.candidates"
                 class="flex items-center gap-3 bg-white/[0.03] rounded px-3 py-2">
              <img *ngIf="c.photoUrl" [src]="c.photoUrl" [alt]="'Photo de ' + c.fullName"
                   class="w-8 h-8 rounded-full object-cover">
              <div class="w-8 h-8 rounded-full bg-white/10 flex items-center justify-center text-xs font-bold text-white"
                   *ngIf="!c.photoUrl">{{ c.fullName.charAt(0) }}</div>
              <span class="text-sm text-white flex-1 truncate">{{ c.fullName }}</span>
              <button *ngIf="!e.published && e.totalVotes === 0" (click)="removeCandidate(e, c)"
                      class="text-gray-400 hover:text-red-400 text-xs" aria-label="Retirer le candidat">✕</button>
            </div>
          </div>

          <!-- Résultats publiés -->
          <div *ngIf="e.published && e.percentages?.length" class="text-xs text-gray-400">
            Résultats : {{ candidateResultsLine(e) }}
          </div>

          <!-- Formulaire ajout candidat (élection non publiée) -->
          <div *ngIf="!e.published" class="border-t border-white/10 pt-3 grid grid-cols-1 sm:grid-cols-[1fr_1fr_auto_auto] gap-2 items-end">
            <input [(ngModel)]="candName[e.id]" placeholder="Nom du candidat"
                   class="bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
            <input [(ngModel)]="candPhoto[e.id]" placeholder="URL photo (Cloudinary)"
                   class="bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
            <input [(ngModel)]="candPresentation[e.id]" placeholder="Présentation courte"
                   class="sm:col-span-2 bg-white/5 border border-white/10 rounded px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
            <button (click)="addCandidate(e)" [disabled]="!candName[e.id]?.trim()"
                    class="bg-wydad-gold/90 hover:bg-wydad-gold disabled:opacity-40 text-black px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
              + Candidat
            </button>
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

  newTitle = '';
  newStartsAt = '';
  newEndsAt = '';

  /** Champs du formulaire candidat, indexés par id d'élection. */
  candName: Record<number, string> = {};
  candPhoto: Record<number, string> = {};
  candPresentation: Record<number, string> = {};

  ngOnInit() {
    this.load();
  }

  private load() {
    this.loading = true;
    // L'ADMIN voit TOUTES les élections via /open (le service enrichit pour lui).
    this.api.getOpenElections().subscribe({
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
    return this.newTitle.trim().length > 0 && this.newStartsAt.length > 0 && this.newEndsAt.length > 0;
  }

  create() {
    if (!this.canCreate()) { return; }
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
    const name = (this.candName[e.id] || '').trim();
    if (!name) { return; }
    this.api.addElectionCandidate(e.id, name,
        (this.candPresentation[e.id] || '').trim() || undefined,
        (this.candPhoto[e.id] || '').trim() || undefined)
      .subscribe({
        next: () => {
          this.toast.success('Candidat ajouté.');
          this.candName[e.id] = ''; this.candPhoto[e.id] = ''; this.candPresentation[e.id] = '';
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
        this.toast.success('Élection clôturée — résultats publiés.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Clôture impossible.');
      }
    });
  }

  candidateResultsLine(e: any): string {
    return (e.candidates || [])
      .map((c: any, i: number) => `${c.fullName} ${e.percentages[i]}% (${e.results[i]})`)
      .join(' · ');
  }
}

/** datetime-local -> ISO avec secondes (le backend attend LocalDateTime). */
function toIso(localValue: string): string {
  return localValue.length === 16 ? `${localValue}:00` : localValue;
}
