import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

/**
 * B.8 — Élections du président : page PUBLIQUE.
 *
 *  - Élections PUBLIÉES (CLOSED + published=true) : résultats figés,
 *    visibles sans connexion (gateway bypass + election-service permitAll).
 *  - Élections EN COURS (OPEN) : participation X/Y visible, SANS détail
 *    par candidat (transparence décidée avec l'équipe produit).
 *
 *  Les deux listes sont rafraîchies par polling 30s (le visiteur reste
 *  sur la page pendant un scrutin, on évite WebSocket pour rester simple).
 */
@Component({
  selector: 'app-elections',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  templateUrl: './elections.component.html',
  styleUrls: ['./elections.component.scss']
})
export class ElectionsComponent implements OnInit, OnDestroy {
  api = inject(ApiService);

  /** Élections en cours (OPEN) — participation X/Y. */
  openElections: any[] = [];
  /** Élections publiées (CLOSED + published=true) — résultats complets. */
  publishedElections: any[] = [];

  loading = true;
  loadError = false;

  private pollHandle: ReturnType<typeof setInterval> | null = null;
  /** 30s — choix produit (cf. plan B.8 §Découpage commits, commit 7). */
  private static readonly POLL_MS = 30_000;

  ngOnInit() {
    this.load();
    this.pollHandle = setInterval(() => this.load(/* silent */ true), ElectionsComponent.POLL_MS);
  }

  ngOnDestroy() {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  retry() {
    this.loadError = false;
    this.load();
  }

  private load(silent = false) {
    if (!silent) { this.loading = true; }
    // Polling parallèle — published et open sont indépendants.
    // On évite switchMap pour gérer les erreurs de chaque flux séparément
    // et ne pas bloquer l'un si l'autre est down.
    this.api.getPublishedElections().subscribe({
      next: (data: any[]) => {
        this.publishedElections = data || [];
        this.loading = false;
        this.loadError = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
    this.api.getOpenElections().subscribe({
      next: (data: any[]) => {
        this.openElections = (data || []).filter(e => e.status === 'OPEN');
        this.loading = false;
        this.loadError = false;
      },
      error: () => {
        // Ne pas écraser loadError — si published a réussi on garde
        // l'affichage publié et on note juste un warning silencieux.
      }
    });
  }

  /** Index du gagnant dans candidates[] à partir de winnerCandidateId. */
  winnerIndex(e: any): number {
    if (!e.winnerCandidateId || !e.candidates) { return -1; }
    return e.candidates.findIndex((c: any) => c.id === e.winnerCandidateId);
  }
}
