import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

/**
 * B.2 — Sondages : page publique listant les sondages actifs du club.
 * Un membre connecté peut voter une seule fois (la règle est prouvée
 * côté serveur : contrainte SQL + userId dérivé du JWT). Résultats
 * affichés depuis les totaux calculés par le backend — aucune donnée
 * de sondage hardcodée.
 */
@Component({
  selector: 'app-sondages',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  templateUrl: './sondages.component.html',
  styleUrls: ['./sondages.component.scss']
})
export class SondagesComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  polls: any[] = [];
  loading = true;
  loadError = false;
  votingPollId: number | null = null;

  /** Pourcentages calculés localement à partir des résultats serveur. */
  percents(poll: any): number[] {
    const total = poll.totalVotes || 0;
    return (poll.resultsPerOption || []).map((n: number) =>
      total === 0 ? 0 : Math.round((n / total) * 100));
  }

  isLoggedIn(): boolean {
    return this.auth.isTokenValid();
  }

  ngOnInit() {
    this.load();
  }

  retry() {
    this.loadError = false;
    this.load();
  }

  private load() {
    this.loading = true;
    this.api.getActivePolls().subscribe({
      next: (data: any[]) => {
        this.polls = data || [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  vote(poll: any, optionIndex: number) {
    if (!this.isLoggedIn() || this.votingPollId !== null) return;
    if (poll.myVoteIndex !== null && poll.myVoteIndex !== undefined) return; // déjà voté

    this.votingPollId = poll.id;
    this.api.votePoll(poll.id, optionIndex).subscribe({
      next: (updated: any) => {
        Object.assign(poll, updated);
        this.toast.success('Vote enregistré, merci !');
        this.votingPollId = null;
      },
      error: (err: any) => {
        this.votingPollId = null;
        this.toast.error(err?.error?.message || 'Impossible d\'enregistrer votre vote.');
      }
    });
  }
}
