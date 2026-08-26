import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';

/**
 * Espace Journaliste (§17) — destination du login pour le rôle JOURNALISTE
 * (correctif bouton « Se connecter », §27). Téléchargement du badge presse
 * PDF+QR (compte VALIDÉ requis), matchs à couvrir issus du calendrier réel,
 * dernières actualités. Thème clair Club (tokens paper/ink).
 */
@Component({
  selector: 'app-dashboard-journaliste',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  templateUrl: './dashboard-journaliste.component.html'
})
export class DashboardJournalisteComponent implements OnInit {
  loading = true;
  loadError = false;
  activeTab: 'badge' | 'matchs' | 'actualites' = 'badge';

  api = inject(ApiService);
  auth = inject(AuthService);
  private toast = inject(ToastService);

  profile: any = null;
  matchs: any[] = [];
  articles: any[] = [];
  downloading = false;

  ngOnInit() {
    this.loadAll();
  }

  retryLoad() {
    this.loadAll();
  }

  private loadAll() {
    this.loading = true;
    this.loadError = false;
    let remaining = 3;

    const finish = () => {
      if (--remaining <= 0) this.loading = false;
    };

    this.auth.getProfile().subscribe({
      next: (p) => { this.profile = p; finish(); },
      error: () => { finish(); }
    });

    this.api.getMatchesByStatut('PROGRAMME').subscribe({
      next: (list) => { this.matchs = list || []; finish(); },
      error: () => { this.matchs = []; finish(); }
    });

    this.api.getArticles().subscribe({
      next: (list) => {
        this.articles = (list || []).slice(0, 6);
        finish();
      },
      error: () => { this.articles = []; finish(); }
    });
  }

  /** Badge presse PDF — endpoint /api/auth/presse/badge (self uniquement). */
  telechargerBadge() {
    if (!this.profile?.email) return;
    this.downloading = true;
    this.api.getBadgePresse(this.profile.email).subscribe({
      next: (blob: Blob) => {
        this.downloading = false;
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'badge-presse-wac.pdf';
        a.click();
        window.URL.revokeObjectURL(url);
        this.toast.show('success', 'Badge presse téléchargé.');
      },
      error: () => {
        this.downloading = false;
        this.toast.show('error', 'Badge indisponible — compte non encore validé par le club ?');
      }
    });
  }

  formatMatchDate(match: any): string {
    if (!match?.date) return '';
    const d = new Date(`${match.date}T${match.heure || '00:00'}`);
    return isNaN(d.getTime()) ? String(match.date)
      : `${d.toLocaleDateString('fr-FR')} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  formatDate(iso: any): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR');
  }
}
