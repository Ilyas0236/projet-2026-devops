import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

/**
 * Page publique « Transparence financière » — liste les rapports financiers
 * publiés par l'ADMIN. Lecture anonyme (endpoint public du content-service).
 */
@Component({
  selector: 'app-transparence',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen bg-paper-0 text-ink-primary pt-28 pb-24">

      <!-- Header -->
      <div class="container-wydad mb-14 text-center relative">
        <span class="text-wydad-red font-bold tracking-[0.3em] uppercase text-xs mb-3 block animate-fade-in-up" style="animation-delay: 0.1s; opacity: 0;">Transparence</span>
        <h1 class="font-display font-black text-hero uppercase tracking-tighter animate-fade-in-up" style="animation-delay: 0.2s; opacity: 0;">
          Transparence <span class="text-wydad-red">financière</span>
        </h1>
        <p class="text-ink-secondary mt-4 max-w-2xl mx-auto animate-fade-in-up" style="animation-delay: 0.3s; opacity: 0;">
          Le Wydad Athletic Club publie ici ses rapports financiers,
          dans un esprit de transparence totale envers ses adhérents et ses supporters.
        </p>
      </div>

      <div class="container-wydad max-w-4xl">
        <!-- Loading -->
        <div *ngIf="loading && !loadError" role="status" class="flex justify-center py-20">
          <div class="w-10 h-10 border-4 border-wydad-red border-t-transparent rounded-full animate-spin"></div>
        </div>

        <!-- Error -->
        <div *ngIf="loadError" class="bg-paper-1 border border-paper-3 rounded-2xl text-center py-16">
          <p class="text-ink-secondary font-display text-lg uppercase tracking-wider mb-4">Impossible de charger les rapports</p>
          <button (click)="loadRapports()" class="px-6 py-2.5 rounded-lg bg-wydad-red hover:bg-red-700 text-white text-sm font-bold transition-colors">Réessayer</button>
        </div>

        <!-- Empty -->
        <div *ngIf="!loading && !loadError && rapports.length === 0"
             class="bg-paper-1 border border-paper-3 rounded-2xl text-center py-16 px-6">
          <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="mx-auto text-ink-tertiary mb-4"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
          <p class="text-ink-secondary">Aucun rapport financier publié pour le moment.</p>
        </div>

        <!-- Liste des rapports -->
        <div class="space-y-4">
          <article *ngFor="let r of rapports"
                   class="bg-paper-1 border border-paper-3 rounded-2xl p-6 flex flex-col md:flex-row md:items-center gap-4 justify-between hover:border-wydad-red/40 transition-colors">
            <div class="min-w-0">
              <div class="flex items-center gap-3 flex-wrap mb-1">
                <span class="shrink-0 w-14 text-center px-2 py-1 rounded-lg bg-wydad-red text-white text-xs font-bold">{{ r.annee }}</span>
                <h2 class="font-display font-bold text-lg">{{ r.titre }}</h2>
              </div>
              <p *ngIf="r.description" class="text-ink-secondary text-sm leading-relaxed">{{ r.description }}</p>
              <p class="text-ink-tertiary text-xs mt-2">Publié le {{ r.publieLe | date:'dd MMMM yyyy' }}</p>
            </div>
            <a [href]="api.getMediaUrl(r.fileUrl)" target="_blank" rel="noopener"
               class="shrink-0 inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-wydad-red hover:bg-red-700 text-white text-xs font-bold uppercase tracking-wider transition-colors">
              <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
              Télécharger
            </a>
          </article>
        </div>
      </div>
    </div>
  `
})
export class TransparenceComponent implements OnInit {
  api = inject(ApiService);

  rapports: any[] = [];
  loading = true;
  loadError = false;

  ngOnInit() {
    this.loadRapports();
  }

  loadRapports() {
    this.loading = true;
    this.loadError = false;
    this.api.getRapportsFinanciers().subscribe({
      next: (list) => {
        this.rapports = Array.isArray(list) ? list : [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }
}
