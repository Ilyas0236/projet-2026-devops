import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

/**
 * Page publique « Palmarès » — lit /content/trophies/public (anonyme).
 * Zéro donnée métier hardcodée : tout vient de l'ADMIN via le backend.
 */
@Component({
  selector: 'app-palmares',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen light-page pt-0 pb-24">

      <!-- Héros Club : rouge profond animé -->
      <div class="club-hero pt-28 pb-20 mb-16">
        <div class="container-wydad text-center relative z-10">
          <span class="club-badge-gold mb-4 block w-fit mx-auto animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Le palmarès</span>
          <h1 class="club-hero-title text-hero uppercase tracking-tighter animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
            Une histoire de victoires
          </h1>
          <span class="club-underline"></span>
          <p class="mt-6 max-w-2xl mx-auto text-white/85 animate-fade-in-up" style="animation-delay: 0.35s; opacity: 0;">
            Les titres qui ont forgé la légende du Wydad Athletic Club depuis 1937.
          </p>
        </div>
      </div>

      <!-- Liste des titres -->
      <div class="container-wydad">
        <!-- Grille trophées -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8" *ngIf="trophies.length > 0">
          <div *ngFor="let t of trophies; let i = index"
               class="club-card p-6 flex items-center gap-5 animate-fade-in-up"
               [style.animation-delay]="(i * 0.08) + 's'"
               style="opacity: 0;">
            <!-- Icône trophée ou image -->
            <div class="shrink-0 w-16 h-16 rounded-xl bg-gradient-to-br from-wydad-red/15 to-wydad-gold/10 border border-wydad-red/25 flex items-center justify-center overflow-hidden">
              <img *ngIf="t.imageUrl" [src]="api.getMediaUrl(t.imageUrl)" [alt]="t.title" class="w-full h-full object-cover">
              <svg *ngIf="!t.imageUrl" class="w-8 h-8 text-wydad-gold-dark" fill="currentColor" viewBox="0 0 24 24">
                <path d="M5 3h14v2h3a1 1 0 0 1 1 1v3c0 2.21-1.79 4-4 4h-.42A7.01 7.01 0 0 1 13 17.92V20h4v2H7v-2h4v-2.08A7.01 7.01 0 0 1 5.42 13H5c-2.21 0-4-1.79-4-4V6a1 1 0 0 1 1-1h3V3zm0 4H3v3a2 2 0 0 0 2 2V7zm14 0v5a2 2 0 0 0 2-2V7h-2z"/>
              </svg>
            </div>
            <div class="min-w-0">
              <div class="flex items-baseline gap-2">
                <span class="font-display font-black text-3xl text-wydad-red">{{ t.count }}</span>
                <span class="text-xs text-ink-tertiary uppercase tracking-wider font-semibold">×</span>
              </div>
              <h3 class="font-display font-bold text-ink-primary uppercase leading-tight truncate">{{ t.title }}</h3>
              <p class="text-xs text-ink-secondary mt-0.5">{{ categoryLabel(t.category) }} · {{ t.season }}</p>
            </div>
          </div>
        </div>

        <!-- Error State -->
        <div *ngIf="loadError" class="club-card text-center py-20">
          <p class="text-ink-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger le palmarès</p>
          <button (click)="loadTrophies()" class="paper-btn-primary px-6 py-2.5">Réessayer</button>
        </div>

        <!-- Loading State -->
        <div *ngIf="loading && !loadError" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8">
          <div *ngFor="let s of [1,2,3]" class="club-card flex items-center gap-5 p-6">
            <div class="club-skeleton w-16 h-16 !rounded-xl shrink-0"></div>
            <div class="space-y-3 flex-1">
              <div class="club-skeleton h-5 w-3/4"></div>
              <div class="club-skeleton h-3 w-1/2"></div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div *ngIf="!loading && !loadError && trophies.length === 0" class="club-card text-center py-20">
          <p class="text-ink-tertiary font-display text-lg uppercase tracking-wider">Palmarès en cours de mise à jour</p>
        </div>
      </div>
    </div>
  `
})
export class PalmaresComponent implements OnInit {
  api = inject(ApiService);

  trophies: any[] = [];
  loading = true;
  loadError = false;

  private static readonly LABELS: Record<string, string> = {
    FOOTBALL: 'Football', BASKETBALL: 'Basketball', HANDBALL: 'Handball',
    VOLLEYBALL: 'Volleyball', NATATION: 'Natation', JUDO: 'Judo', ATHLETISME: 'Athlétisme'
  };

  ngOnInit() {
    this.loadTrophies();
  }

  loadTrophies() {
    this.loading = true;
    this.loadError = false;
    this.api.getPublicTrophies().subscribe({
      next: (list) => {
        this.trophies = list || [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  categoryLabel(category: string): string {
    return PalmaresComponent.LABELS[category] || category;
  }
}
