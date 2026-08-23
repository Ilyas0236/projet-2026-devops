import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * B.2 — Gestion des sondages par l'ADMIN : création (question + options
 * dynamiques) et clôture. Les règles de sécurité sont prouvées côté
 * backend (@PreAuthorize ADMIN, un vote par membre via contrainte SQL).
 */
@Component({
  selector: 'app-admin-sondages',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Sondages</h2>
        <p class="text-sm text-gray-400 mt-1">
          Créez des sondages pour la communauté Wydad. Un seul vote par membre, résultats calculés côté serveur.
        </p>
      </div>

      <!-- Formulaire de création -->
      <div class="bg-white/5 border border-white/10 rounded-lg p-6 space-y-4">
        <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm">Nouveau sondage</h3>

        <input [(ngModel)]="newQuestion" placeholder="Question du sondage"
               class="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">

        <div class="space-y-2">
          <div *ngFor="let opt of newOptions; let i = index" class="flex gap-2">
            <input [(ngModel)]="newOptions[i]" [placeholder]="'Option ' + (i + 1)"
                   class="flex-1 bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:border-wydad-red">
            <button (click)="removeOption(i)" [disabled]="newOptions.length <= 2"
                    class="px-3 text-gray-400 hover:text-red-400 disabled:opacity-30 disabled:cursor-not-allowed">✕</button>
          </div>
        </div>
        <button (click)="addOption()" class="text-xs text-wydad-red hover:underline">+ Ajouter une option</button>

        <button (click)="create()" [disabled]="creating || !canCreate()"
                class="bg-wydad-red hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-white px-6 py-2.5 rounded font-bold text-sm transition-colors">
          {{ creating ? 'Création…' : 'Créer le sondage' }}
        </button>
      </div>

      <!-- Liste des sondages actifs -->
      <div *ngIf="loading" class="flex justify-center py-16">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading" class="space-y-3">
        <div *ngFor="let poll of polls" class="bg-white/5 border border-white/10 rounded-lg p-5 flex justify-between items-start gap-4">
          <div class="min-w-0">
            <h4 class="font-bold text-white truncate">{{ poll.question }}</h4>
            <p class="text-xs text-gray-400 mt-1">{{ poll.options.join(' · ') }}</p>
            <p class="text-xs text-gray-500 mt-2">{{ poll.totalVotes }} vote(s)
               <span *ngIf="!poll.active" class="text-red-300 ml-2">Clôturé</span></p>
          </div>
          <button *ngIf="poll.active" (click)="close(poll)"
                  class="flex-shrink-0 border border-white/20 hover:border-red-400 hover:text-red-300 text-gray-300 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors">
            Clôturer
          </button>
        </div>

        <p *ngIf="polls.length === 0" class="text-gray-500 text-sm text-center py-8">Aucun sondage créé.</p>
      </div>
    </div>
  `
})
export class AdminSondagesComponent implements OnInit {
  api = inject(ApiService);
  toast = inject(ToastService);

  polls: any[] = [];
  loading = true;
  error = '';
  creating = false;

  newQuestion = '';
  newOptions: string[] = ['', ''];

  ngOnInit() {
    this.load();
  }

  private load() {
    this.loading = true;
    this.api.getActivePolls().subscribe({
      next: (data: any[]) => {
        // Le endpoint "actifs" sert aussi de base admin ; on y voit le total
        // de votes et les resultats calcules serveur.
        this.polls = data || [];
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les sondages.';
        this.loading = false;
      }
    });
  }

  canCreate(): boolean {
    return this.newQuestion.trim().length > 0
      && this.newOptions.filter(o => o.trim().length > 0).length >= 2;
  }

  addOption() {
    this.newOptions.push('');
  }

  removeOption(i: number) {
    if (this.newOptions.length > 2) this.newOptions.splice(i, 1);
  }

  create() {
    if (!this.canCreate()) return;
    this.creating = true;
    this.api.createPoll(this.newQuestion.trim(),
        this.newOptions.map(o => o.trim()).filter(o => o.length > 0))
      .subscribe({
        next: () => {
          this.toast.success('Sondage créé.');
          this.newQuestion = '';
          this.newOptions = ['', ''];
          this.creating = false;
          this.load();
        },
        error: (err: any) => {
          this.creating = false;
          this.toast.error(err?.error?.message || 'Création impossible.');
        }
      });
  }

  close(poll: any) {
    this.api.closePoll(poll.id).subscribe({
      next: () => {
        this.toast.success('Sondage clôturé.');
        this.load();
      },
      error: (err: any) => {
        this.toast.error(err?.error?.message || 'Clôture impossible.');
      }
    });
  }
}
