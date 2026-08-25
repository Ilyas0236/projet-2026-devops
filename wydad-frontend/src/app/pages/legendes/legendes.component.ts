import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

/**
 * Page publique « Légendes » (Hall of Fame) — lit /content/legends/public
 * anonymement. Zéro donnée métier hardcodée : tout vient de l'ADMIN.
 */
@Component({
  selector: 'app-legendes',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen light-page pt-0 pb-24">

      <!-- Héros Club : rouge profond animé -->
      <div class="club-hero pt-28 pb-20 mb-16">
        <div class="container-wydad text-center relative z-10">
          <span class="club-badge-gold mb-4 block w-fit mx-auto animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Hall of Fame</span>
          <h1 class="club-hero-title text-hero uppercase tracking-tighter relative animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
            Les légendes du club
          </h1>
          <span class="club-underline"></span>
          <p class="mt-6 max-w-2xl mx-auto text-white/85 relative animate-fade-in-up" style="animation-delay: 0.35s; opacity: 0;">
            Ceux et celles qui ont marqué l'histoire du Wydad Athletic Club à jamais.
          </p>
        </div>
      </div>

      <div class="container-wydad">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8" *ngIf="legends.length > 0">
          <div *ngFor="let l of legends; let i = index"
               class="club-card club-photo-card animate-fade-in-up"
               [style.animation-delay]="(i * 0.08) + 's'"
               style="opacity: 0;">

            <!-- Photo ou initiales -->
            <div class="club-photo-frame aspect-[4/3]">
              <div *ngIf="l.imageUrl" class="club-photo-bg absolute inset-0 bg-cover bg-top"
                   [style.backgroundImage]="'url(' + api.getMediaUrl(l.imageUrl) + ')'"></div>
              <div *ngIf="!l.imageUrl" class="absolute inset-0 flex items-center justify-center">
                <span class="font-display font-black text-7xl text-paper-3 uppercase">
                  {{ initials(l.name) }}
                </span>
              </div>
              <div class="club-photo-veil"></div>
              <!-- Période -->
              <span class="absolute top-3 right-3 z-10 bg-gray-900/80 backdrop-blur-sm text-white text-xs font-bold px-3 py-1.5 rounded-lg">
                {{ periode(l) }}
              </span>
            </div>

            <div class="p-5">
              <h3 class="font-display font-bold text-lg text-ink-primary uppercase leading-tight">{{ l.name }}</h3>
              <p *ngIf="l.nickname" class="text-wydad-gold-dark text-sm font-semibold mt-0.5">« {{ l.nickname }} »</p>
              <p class="text-ink-secondary text-sm mt-2">{{ l.role }}</p>
              <p *ngIf="l.biography" class="text-ink-tertiary text-xs mt-3 leading-relaxed line-clamp-3">{{ l.biography }}</p>
            </div>
          </div>
        </div>

        <!-- Error State -->
        <div *ngIf="loadError" class="club-card text-center py-20">
          <p class="text-ink-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger les légendes</p>
          <button (click)="loadLegends()" class="paper-btn-primary px-6 py-2.5">Réessayer</button>
        </div>

        <!-- Loading State -->
        <div *ngIf="loading && !loadError" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          <div *ngFor="let s of [1,2,3]" class="club-card p-0 overflow-hidden">
            <div class="club-skeleton aspect-[4/3] rounded-none"></div>
            <div class="p-5 space-y-3">
              <div class="club-skeleton h-4 w-2/3"></div>
              <div class="club-skeleton h-3 w-1/2"></div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div *ngIf="!loading && !loadError && legends.length === 0" class="club-card text-center py-20">
          <p class="text-ink-tertiary font-display text-lg uppercase tracking-wider">Hall of Fame en cours de préparation</p>
        </div>
      </div>
    </div>
  `
})
export class LegendesComponent implements OnInit {
  api = inject(ApiService);

  legends: any[] = [];
  loading = true;
  loadError = false;

  ngOnInit() {
    this.loadLegends();
  }

  loadLegends() {
    this.loading = true;
    this.loadError = false;
    this.api.getPublicLegends().subscribe({
      next: (list) => {
        this.legends = list || [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  periode(l: any): string {
    return l.yearTo ? `${l.yearFrom} – ${l.yearTo}` : `${l.yearFrom} –`;
  }

  initials(name: string): string {
    return (name || '?').split(/\s+/).map(p => p[0]).join('').slice(0, 2);
  }
}
