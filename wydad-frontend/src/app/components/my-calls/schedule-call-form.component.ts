import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * Phase 5 — formulaire « Programmer un appel » pour ENTRAINEUR / PRESIDENT.
 * - Entraîneur : cible son équipe (catégorie forcée côté serveur depuis sa fiche).
 * - Président : cible les adhérents PREMIUM ou une réunion interne.
 * La création est re-vérifiée côté serveur ; ce formulaire n'est qu'une porte d'entrée UX.
 */
@Component({
  selector: 'app-schedule-call-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="paper-card rounded-3xl p-8">
      <h3 class="section-title-light font-display text-2xl uppercase tracking-wider text-ink-primary">Programmer un appel</h3>
      <p class="text-ink-tertiary text-xs uppercase tracking-widest mt-1 mb-6">Vidéo ou audio · notification envoyée aux conviés</p>

      <div class="grid gap-4 md:grid-cols-2">
        <div class="md:col-span-2">
          <label class="block text-xs font-bold uppercase tracking-wider text-ink-secondary mb-1">Titre</label>
          <input type="text" [(ngModel)]="title" maxlength="120" placeholder="Briefing avant match…"
                 class="w-full px-4 py-2.5 rounded-xl border border-paper-3 bg-white text-sm text-ink-primary focus:outline-none focus:border-wydad-red/60" />
        </div>

        <!-- Président : choix du public -->
        <div *ngIf="isPresident" class="md:col-span-2">
          <label class="block text-xs font-bold uppercase tracking-wider text-ink-secondary mb-1">Public convié</label>
          <select [(ngModel)]="target"
                  class="w-full px-4 py-2.5 rounded-xl border border-paper-3 bg-white text-sm text-ink-primary focus:outline-none focus:border-wydad-red/60">
            <option value="PREMIUM">Adhérents PREMIUM</option>
            <option value="EQUIPE">Équipe (via entraîneur — délégué)</option>
          </select>
          <p class="text-ink-tertiary text-[11px] mt-1">Pour un joueur précis ou un staff, passez par l'entraîneur concerné.</p>
        </div>

        <!-- Entraîneur : rappel de la cible forcée -->
        <div *ngIf="isCoach" class="md:col-span-2 p-3 rounded-xl bg-paper-2 border border-paper-3 text-xs text-ink-secondary">
          L'appel sera adressé à votre équipe
          <span class="font-bold uppercase">{{ sportLabel }}</span> — catégorie
          <span class="font-bold uppercase">{{ categoryLabel }}</span> (définies par votre fiche staff).
        </div>

        <div>
          <label class="block text-xs font-bold uppercase tracking-wider text-ink-secondary mb-1">Date & heure</label>
          <input type="datetime-local" [(ngModel)]="scheduledAt"
                 class="w-full px-4 py-2.5 rounded-xl border border-paper-3 bg-white text-sm text-ink-primary focus:outline-none focus:border-wydad-red/60" />
          <p class="text-ink-tertiary text-[11px] mt-1">Vide = immédiat</p>
        </div>

        <div>
          <label class="block text-xs font-bold uppercase tracking-wider text-ink-secondary mb-1">Durée (min)</label>
          <input type="number" [(ngModel)]="durationMinutes" min="5" max="240" step="5"
                 class="w-full px-4 py-2.5 rounded-xl border border-paper-3 bg-white text-sm text-ink-primary focus:outline-none focus:border-wydad-red/60" />
        </div>

        <div class="md:col-span-2 flex justify-end gap-2 mt-2">
          <button type="button" (click)="reset()"
                  class="px-5 py-2 rounded-xl border border-paper-3 bg-white text-ink-secondary text-xs font-bold uppercase tracking-wider hover:border-wydad-red/50 transition-all">
            Effacer
          </button>
          <button type="button" (click)="submit()" [disabled]="submitting"
                  class="px-6 py-2 rounded-xl bg-wydad-red text-white text-xs font-bold uppercase tracking-wider hover:bg-wydad-red/90 transition-all disabled:opacity-50">
            {{ submitting ? 'Programmation…' : 'Programmer l\'appel' }}
          </button>
        </div>
      </div>
    </div>
  `,
})
export class ScheduleCallFormComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  @Output() created = new EventEmitter<void>();

  isPresident = false;
  isCoach = false;
  sportLabel = '';
  categoryLabel = '';

  title = '';
  target: 'PREMIUM' | 'EQUIPE' = 'PREMIUM';
  scheduledAt = '';
  durationMinutes = 30;
  submitting = false;

  ngOnInit() {
    const role = this.auth.getTokenRole();
    this.isPresident = role === 'PRESIDENT';
    this.isCoach = role === 'ENTRAINEUR';
    if (this.isCoach) this.loadMyStaffFiche();
  }

  /** Rappel visuel de la catégorie forcée : on lit la fiche staff du coach. */
  private loadMyStaffFiche() {
    const userId = Number(this.auth.decodeToken()?.sub) || 0;
    if (!userId) return;
    this.api.getStaffByUserId(userId).subscribe({
      next: (fiche) => {
        this.sportLabel = fiche?.sportType ?? '';
        this.categoryLabel = fiche?.assignedCategory ?? fiche?.category ?? '';
      },
      error: () => { /* affichage seul */ },
    });
  }

  submit() {
    if (!this.title.trim()) { this.toast.error('Le titre est obligatoire'); return; }

    const body: any = {
      title: this.title.trim(),
      durationMinutes: this.durationMinutes,
      targetType: this.isCoach ? 'CATEGORIE_EQUIPE' : (this.target === 'PREMIUM' ? 'PREMIUM' : 'UTILISATEURS'),
    };
    if (this.scheduledAt) body.scheduledAt = new Date(this.scheduledAt).toISOString();

    this.submitting = true;
    this.api.createCall(body).subscribe({
      next: () => {
        this.toast.success('Appel programmé — les conviés sont notifiés');
        this.reset();
        this.created.emit();
      },
      error: (e) => this.toast.error(e?.error?.message || 'Programmation impossible'),
      complete: () => (this.submitting = false),
    });
  }

  reset() {
    this.title = '';
    this.scheduledAt = '';
    this.durationMinutes = 30;
  }
}
