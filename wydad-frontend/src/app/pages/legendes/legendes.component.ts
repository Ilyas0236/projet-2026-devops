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
    <div class="min-h-screen bg-surface-0 text-white pt-28 pb-24">

      <!-- Header -->
      <div class="container-wydad mb-16 text-center relative">
        <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[300px] bg-red-glow opacity-30 pointer-events-none"></div>
        <span class="text-wydad-red font-bold tracking-[0.3em] uppercase text-xs mb-3 block relative z-10 animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Hall of Fame</span>
        <h1 class="font-display font-black text-hero uppercase tracking-tighter relative z-10 animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
          Les <span class="text-wydad-red">légendes</span> du club
        </h1>
        <p class="text-text-secondary mt-4 max-w-2xl mx-auto relative z-10 animate-fade-in-up" style="animation-delay: 0.3s; opacity: 0;">
          Ceux et celles qui ont marqué l'histoire du Wydad Athletic Club à jamais.
        </p>
      </div>

      <div class="container-wydad">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" *ngIf="legends.length > 0">
          <div *ngFor="let l of legends; let i = index"
               class="group glass rounded-2xl overflow-hidden hover:border-wydad-red/30 transition-all duration-500 hover:shadow-card-hover animate-fade-in-up"
               [style.animation-delay]="(i * 0.06) + 's'"
               style="opacity: 0;">

            <!-- Photo ou initiales -->
            <div class="aspect-[4/3] relative bg-surface-2 overflow-hidden">
              <div *ngIf="l.imageUrl" class="absolute inset-0 bg-cover bg-top group-hover:scale-105 transition-transform duration-700"
                   [style.backgroundImage]="'url(' + api.getMediaUrl(l.imageUrl) + ')'"></div>
              <div *ngIf="!l.imageUrl" class="absolute inset-0 flex items-center justify-center">
                <span class="font-display font-black text-6xl text-surface-4 uppercase">
                  {{ initials(l.name) }}
                </span>
              </div>
              <!-- Période -->
              <span class="absolute top-3 right-3 z-10 glass text-white text-xs font-bold px-3 py-1.5 rounded">
                {{ periode(l) }}
              </span>
            </div>

            <div class="p-5">
              <h3 class="font-display font-bold text-lg text-white uppercase leading-tight">{{ l.name }}</h3>
              <p *ngIf="l.nickname" class="text-wydad-gold text-sm font-semibold mt-0.5">« {{ l.nickname }} »</p>
              <p class="text-text-secondary text-sm mt-2">{{ l.role }}</p>
              <p *ngIf="l.biography" class="text-text-tertiary text-xs mt-3 leading-relaxed line-clamp-3">{{ l.biography }}</p>
            </div>
          </div>
        </div>

        <!-- Error State -->
        <div *ngIf="loadError" class="glass rounded-2xl text-center py-20">
          <p class="text-text-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger les légendes</p>
          <button (click)="loadLegends()" class="wydad-btn-primary px-6 py-2.5">Réessayer</button>
        </div>

        <!-- Loading State -->
        <div *ngIf="loading && !loadError" class="glass rounded-2xl text-center py-20">
          <p class="text-text-tertiary font-display text-lg uppercase tracking-wider">Chargement...</p>
        </div>

        <!-- Empty State -->
        <div *ngIf="!loading && !loadError && legends.length === 0" class="glass rounded-2xl text-center py-20">
          <p class="text-text-tertiary font-display text-lg uppercase tracking-wider">Hall of Fame en cours de préparation</p>
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
