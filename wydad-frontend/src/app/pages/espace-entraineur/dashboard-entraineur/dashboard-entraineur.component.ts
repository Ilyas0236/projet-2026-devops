import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';
import { MyCallsComponent } from '../../../components/my-calls/my-calls.component';
import { ScheduleCallFormComponent } from '../../../components/my-calls/schedule-call-form.component';

/**
 * Espace Entraîneur — destination du login pour le rôle ENTRAINEUR
 * (correctif bouton « Se connecter », §27). Vue de pilotage 100% réelle :
 * effectif et séances de SA discipline+catégorie (isolation §6/§24),
 * matchs programmés de sa catégorie, appels vidéo LiveKit (Phase 5).
 * Thème clair Club (tokens paper/ink).
 */
@Component({
  selector: 'app-dashboard-entraineur',
  standalone: true,
  imports: [CommonModule, ErrorBannerComponent, MyCallsComponent, ScheduleCallFormComponent],
  templateUrl: './dashboard-entraineur.component.html'
})
export class DashboardEntraineurComponent implements OnInit {
  loading = true;
  loadError = false;
  activeTab: 'effectif' | 'seances' | 'matchs' | 'video' = 'effectif';

  api = inject(ApiService);
  auth = inject(AuthService);

  /** Fiche staff rattachée au compte entraîneur (sports-service). */
  staff: any = null;
  joueurs: any[] = [];
  seances: any[] = [];
  matchs: any[] = [];

  ngOnInit() {
    this.loadAll();
  }

  retryLoad() {
    this.loadAll();
  }

  private loadAll() {
    this.loading = true;
    this.loadError = false;
    const userId = this.auth.getCurrentUserId();
    if (!userId) {
      this.loadError = true;
      this.loading = false;
      return;
    }

    this.api.getStaffByUserId(userId).subscribe({
      next: (staff) => {
        this.staff = staff;
        this.chargerDonneesCategorie();
      },
      error: () => {
        // Pas de fiche staff : le compte existe mais n'est rattaché à
        // aucune discipline/catégorie — écran vide assumé, jamais simulé.
        this.staff = null;
        this.loading = false;
      }
    });
  }

  private chargerDonneesCategorie() {
    const sport = this.staff?.sportType as string | undefined;
    const categorie = (this.staff?.assignedCategory || this.staff?.category) as string | undefined;
    let remaining = sport && categorie ? 3 : 1;

    const finish = () => {
      if (--remaining <= 0) this.loading = false;
    };

    if (!sport || !categorie) {
      finish();
      return;
    }

    this.api.getPlayersByCategory(sport, categorie).subscribe({
      next: (list) => { this.joueurs = list || []; finish(); },
      error: () => { this.joueurs = []; finish(); }
    });

    this.api.getSessionsByCategory(sport, categorie).subscribe({
      next: (list) => { this.seances = list || []; finish(); },
      error: () => { this.seances = []; finish(); }
    });

    this.api.getMatches().subscribe({
      next: (list) => {
        // Isolation §6 : seuls les matchs de SA discipline+catégorie.
        this.matchs = (list || []).filter(m =>
          (!m.sport || m.sport === sport) &&
          (!m.categorie || m.categorie === categorie));
        finish();
      },
      error: () => { this.matchs = []; finish(); }
    });
  }

  formatSeance(seance: any): string {
    if (!seance?.date) return '';
    const d = new Date(`${seance.date}T${seance.heure || '00:00'}`);
    return isNaN(d.getTime()) ? String(seance.date)
      : `${d.toLocaleDateString('fr-FR')} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  formatMatchDate(match: any): string {
    return this.formatSeance(match);
  }
}
