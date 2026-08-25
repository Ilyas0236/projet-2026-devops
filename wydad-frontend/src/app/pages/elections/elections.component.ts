import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

/**
 * B.8 — Élections du président : page PUBLIQUE des résultats.
 * Visible y compris par les visiteurs non connectés — la gateway laisse
 * passer GET /api/elections/published** sans JWT et le service ne peupl
 * les résultats QUE pour une élection clôturée.
 */
@Component({
  selector: 'app-elections',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  templateUrl: './elections.component.html',
  styleUrls: ['./elections.component.scss']
})
export class ElectionsComponent implements OnInit {
  api = inject(ApiService);

  elections: any[] = [];
  loading = true;
  loadError = false;

  ngOnInit() {
    this.load();
  }

  retry() {
    this.loadError = false;
    this.load();
  }

  private load() {
    this.loading = true;
    this.api.getPublishedElections().subscribe({
      next: (data: any[]) => {
        this.elections = data || [];
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  /** Index du gagnant dans candidates[] à partir de winnerCandidateId. */
  winnerIndex(e: any): number {
    if (!e.winnerCandidateId || !e.candidates) { return -1; }
    return e.candidates.findIndex((c: any) => c.id === e.winnerCandidateId);
  }
}
