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
    <div class="min-h-screen bg-surface-0 text-white pt-28 pb-24">

      <!-- Header -->
      <div class="container-wydad mb-16 text-center relative">
        <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[300px] bg-red-glow opacity-30 pointer-events-none"></div>
        <span class="text-wydad-red font-bold tracking-[0.3em] uppercase text-xs mb-3 block relative z-10 animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Notre antre</span>
        <h1 class="font-display font-black text-hero uppercase tracking-tighter relative z-10 animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
          Le <span class="text-wydad-red">Stade</span>
        </h1>
        <p *ngIf="stadium?.name" class="text-text-secondary mt-4 max-w-2xl mx-auto relative z-10 animate-fade-in-up" style="animation-delay: 0.3s; opacity: 0;">
          {{ stadium.name }}{{ stadium.city ? ' · ' + stadium.city : '' }}
        </p>
      </div>

      <div class="container-wydad" *ngIf="stadium">
        <!-- Cartes principales -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-12">
          <div class="glass rounded-2xl p-8 text-center animate-fade-in-up" style="opacity: 0;">
            <span class="font-display font-black text-4xl text-wydad-gold block">{{ stadium.capacity | number }}</span>
            <span class="text-xs text-text-muted uppercase tracking-wider font-semibold">Places</span>
          </div>
          <div class="glass rounded-2xl p-8 text-center animate-fade-in-up" style="opacity: 0;">
            <span class="font-display font-black text-4xl text-wydad-red block">{{ stadium.openedYear || '—' }}</span>
            <span class="text-xs text-text-muted uppercase tracking-wider font-semibold">Inauguration</span>
          </div>
          <div class="glass rounded-2xl p-8 text-center animate-fade-in-up" style="opacity: 0;">
            <span class="font-display font-bold text-xl text-white block leading-tight">{{ stadium.city || '—' }}</span>
            <span class="text-xs text-text-muted uppercase tracking-wider font-semibold">Ville</span>
          </div>
 </div>

        <!-- Adresse & accès -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div *ngIf="stadium.address" class="glass rounded-2xl p-6">
            <h3 class="font-display font-bold text-white uppercase text-sm tracking-wider mb-3">📍 Adresse</h3>
            <p class="text-text-secondary text-sm leading-relaxed">{{ stadium.address }}</p>
          </div>
          <div *ngIf="stadium.accessInfo" class="glass rounded-2xl p-6">
            <h3 class="font-display font-bold text-white uppercase text-sm tracking-wider mb-3">🚋 Accès</h3>
            <p class="text-text-secondary text-sm leading-relaxed whitespace-pre-line">{{ stadium.accessInfo }}</p>
          </div>
          <div *ngIf="stadium.history" class="glass rounded-2xl p-6 md:col-span-2">
            <h3 class="font-display font-bold text-white uppercase text-sm tracking-wider mb-3">📖 Histoire</h3>
            <p class="text-text-secondary text-sm leading-relaxed whitespace-pre-line">{{ stadium.history }}</p>
          </div>
        </div>
      </div>

      <!-- Loading / Error / Empty -->
      <div class="container-wydad" *ngIf="loading && !loadError">
        <div class="glass rounded-2xl text-center py-20">
          <p class="text-text-tertiary font-display text-lg uppercase tracking-wider">Chargement...</p>
        </div>
      </div>
      <div class="container-wydad" *ngIf="loadError">
        <div class="glass rounded-2xl text-center py-20">
          <p class="text-text-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger</p>
          <button (click)="load()" class="wydad-btn-primary px-6 py-2.5">Réessayer</button>
        </div>
      </div>
      <div class="container-wydad" *ngIf="!loading && !loadError && !stadium">
        <div class="glass rounded-2xl text-center py-20">
          <p class="text-text-tertiary font-display text-lg uppercase tracking-wider">Informations bientôt disponibles</p>
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
