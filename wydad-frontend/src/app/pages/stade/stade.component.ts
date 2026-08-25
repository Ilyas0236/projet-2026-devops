import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

/**
 * Page publique « Stade » — lit la clé de configuration "stadium_info"
 * (source de vérité ADMIN, prouvée par StadiumInfoSecurityTest).
 * Zéro donnée métier hardcodée : si l'ADMIN n'a rien saisi, la page
 * affiche un état vide explicite.
 */
@Component({
  selector: 'app-stade',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen light-page pt-0 pb-24">

      <!-- Héros Club : rouge profond animé -->
      <div class="club-hero pt-28 pb-20 mb-16">
        <div class="container-wydad text-center relative z-10">
          <span class="club-badge-gold mb-4 block w-fit mx-auto animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Notre antre</span>
          <h1 class="club-hero-title text-hero uppercase tracking-tighter animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
            Le Stade
          </h1>
          <span class="club-underline"></span>
          <p *ngIf="stadium?.name" class="mt-6 max-w-2xl mx-auto text-white/85 animate-fade-in-up" style="animation-delay: 0.35s; opacity: 0;">
            {{ stadium.name }}{{ stadium.city ? ' · ' + stadium.city : '' }}
          </p>
        </div>
      </div>

      <div class="container-wydad" *ngIf="stadium">
        <!-- Cartes principales -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-8 mb-12">
          <div class="club-card p-8 text-center animate-fade-in-up" style="opacity: 0;">
            <span class="font-display font-black text-4xl text-wydad-red block">{{ stadium.capacity | number }}</span>
            <span class="text-xs text-ink-tertiary uppercase tracking-wider font-semibold">Places</span>
          </div>
          <div class="club-card p-8 text-center animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">
            <span class="font-display font-black text-4xl text-wydad-gold-dark block">{{ stadium.openedYear || '—' }}</span>
            <span class="text-xs text-ink-tertiary uppercase tracking-wider font-semibold">Inauguration</span>
          </div>
          <div class="club-card p-8 text-center animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
            <span class="font-display font-bold text-xl text-ink-primary block leading-tight">{{ stadium.city || '—' }}</span>
            <span class="text-xs text-ink-tertiary uppercase tracking-wider font-semibold">Ville</span>
          </div>
 </div>

        <!-- Adresse & accès -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div *ngIf="stadium.address" class="club-card p-6">
            <h3 class="font-display font-bold text-ink-primary uppercase text-sm tracking-wider mb-3">📍 Adresse</h3>
            <p class="text-ink-secondary text-sm leading-relaxed">{{ stadium.address }}</p>
          </div>
          <div *ngIf="stadium.accessInfo" class="club-card p-6">
            <h3 class="font-display font-bold text-ink-primary uppercase text-sm tracking-wider mb-3">🚋 Accès</h3>
            <p class="text-ink-secondary text-sm leading-relaxed whitespace-pre-line">{{ stadium.accessInfo }}</p>
          </div>
          <div *ngIf="stadium.history" class="club-card p-6 md:col-span-2">
            <h3 class="font-display font-bold text-ink-primary uppercase text-sm tracking-wider mb-3">📖 Histoire</h3>
            <p class="text-ink-secondary text-sm leading-relaxed whitespace-pre-line">{{ stadium.history }}</p>
          </div>
        </div>
      </div>

      <!-- Loading / Error / Empty -->
      <div class="container-wydad" *ngIf="loading && !loadError">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-8 mb-8">
          <div *ngFor="let s of [1,2,3]" class="club-card p-8 text-center space-y-4">
            <div class="club-skeleton h-9 w-16 mx-auto"></div>
            <div class="club-skeleton h-3 w-20 mx-auto"></div>
          </div>
        </div>
      </div>
      <div class="container-wydad" *ngIf="loadError">
        <div class="club-card text-center py-20">
          <p class="text-ink-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger</p>
          <button (click)="load()" class="paper-btn-primary px-6 py-2.5">Réessayer</button>
        </div>
      </div>
      <div class="container-wydad" *ngIf="!loading && !loadError && !stadium">
        <div class="club-card text-center py-20">
          <p class="text-ink-tertiary font-display text-lg uppercase tracking-wider">Informations bientôt disponibles</p>
        </div>
      </div>
    </div>
  `
})
export class StadeComponent implements OnInit {
  api = inject(ApiService);

  stadium: any = null;
  loading = true;
  loadError = false;

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.loadError = false;
    this.api.getClubSetting('stadium_info').subscribe({
      next: (data) => {
        this.stadium = data;
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }
}
