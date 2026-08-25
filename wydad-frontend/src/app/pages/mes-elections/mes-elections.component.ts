import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ErrorBannerComponent } from '../../components/error-banner/error-banner.component';

/**
 * B.8 — Élections du président : espace ADHÉRENT.
 * Liste les élections ouvertes avec l'état de vote de l'utilisateur
 * (myVoteIndex / canVote peuplés par le backend depuis le JWT).
 * Le vote est définitif ; un seul vote par membre est prouvé côté serveur.
 */
@Component({
  selector: 'app-mes-elections',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent],
  templateUrl: './mes-elections.component.html',
  styleUrls: ['./mes-elections.component.scss']
})
export class MesElectionsComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  elections: any[] = [];
  loading = true;
  loadError = false;
  /** Id de l'élection en cours de vote (anti double-clic). */
  votingElectionId: number | null = null;

  ngOnInit() {
    this.load();
  }

  retry() {
    this.loadError = false;
    this.load();
  }

  private load() {
    this.loading = true;
    this.api.getOpenElections().subscribe({
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

  vote(election: any, candidateId: number) {
    if (this.votingElectionId !== null) { return; }
    // Déjà voté : le backend refuse de toute façon (409) mais on évite l'appel.
    if (election.myVoteIndex !== null && election.myVoteIndex !== undefined) { return; }

    this.votingElectionId = election.id;
    this.api.voteElection(election.id, candidateId).subscribe({
      next: (updated: any) => {
        Object.assign(election, updated);
        this.toast.success('Votre vote a été enregistré. Merci de votre participation !');
        this.votingElectionId = null;
      },
      error: (err: any) => {
        this.votingElectionId = null;
        this.toast.error(err?.error?.message || 'Impossible d\'enregistrer votre vote.');
      }
    });
  }
}
