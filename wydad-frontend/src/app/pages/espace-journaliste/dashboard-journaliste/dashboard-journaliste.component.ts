import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';

/**
 * Espace Journaliste (B.17) — destination du login pour le rôle JOURNALISTE.
 *
 *  - onglet "Mon badge" : upload photo + téléchargement du badge par accréditation validée
 *  - onglet "Matchs à couvrir" : pour chaque match PROGRAMME, bouton "Demander
 *    accréditation" (grisé si pas de photo de profil) ; badge téléchargeable
 *    pour les accréditations VALIDÉES.
 *  - onglet "Mes demandes" : récapitulatif des demandes avec statut + motif
 *    de refus éventuel.
 *  - onglet "Actualités" : feed public.
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
  activeTab: 'badge' | 'matchs' | 'demandes' | 'actualites' = 'badge';

  api = inject(ApiService);
  auth = inject(AuthService);
  private toast = inject(ToastService);
  router = inject(Router);

  profile: any = null;
  matchs: any[] = [];
  /** Demandes d'accréditation du journaliste connecté (B.17). */
  mesDemandes: any[] = [];
  articles: any[] = [];

  /** Photo en cours d'upload. */
  photoFile: File | null = null;
  photoFileName = '';
  uploadingPhoto = false;

  /** Map matchId -> accreditation (pour affichage état par match). */
  demandesByMatchId: { [matchId: number]: any } = {};
  /** matchId pour lequel une demande est en cours. */
  busyMatchId: number | null = null;

  ngOnInit() {
    this.loadAll();
  }

  retryLoad() {
    this.loadAll();
  }

  private loadAll() {
    this.loading = true;
    this.loadError = false;
    // 4 sources : profile, matchs, demandes, articles
    let remaining = 4;
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

    this.api.getMyPressAccreditations().subscribe({
      next: (list) => {
        this.mesDemandes = list || [];
        this.demandesByMatchId = {};
        for (const d of this.mesDemandes) {
          if (d && d.matchId != null) this.demandesByMatchId[d.matchId] = d;
        }
        finish();
      },
      error: () => { this.mesDemandes = []; this.demandesByMatchId = {}; finish(); }
    });

    this.api.getArticles().subscribe({
      next: (list) => {
        this.articles = (list || []).slice(0, 6);
        finish();
      },
      error: () => { this.articles = []; finish(); }
    });
  }

  // ───────────────────── Photo de profil ─────────────────────

  onPhotoFileSelected(event: any) {
    const input = event.target as HTMLInputElement;
    this.photoFile = input.files && input.files.length ? input.files[0] : null;
    this.photoFileName = this.photoFile?.name || '';
  }

  uploaderPhoto() {
    if (!this.photoFile) return;
    this.uploadingPhoto = true;
    this.api.uploadMyPhoto(this.photoFile).subscribe({
      next: (res) => {
        this.uploadingPhoto = false;
        this.photoFile = null;
        this.photoFileName = '';
        this.toast.show('success', 'Photo de profil enregistrée.');
        // Recharge le profil pour que photoUrl soit à jour
        this.auth.getProfile().subscribe({ next: (p) => this.profile = p });
      },
      error: (err) => {
        this.uploadingPhoto = false;
        this.toast.show('error', err?.error?.message || err?.error?.error || 'Échec de l\'upload de la photo.');
      }
    });
  }

  // ───────────────────── Accréditations ─────────────────────

  hasPhoto(): boolean {
    return !!(this.profile && this.profile.photoUrl && String(this.profile.photoUrl).trim().length > 0);
  }

  /** État d'une demande pour un match : 'NONE' | 'PENDING' | 'VALIDATED' | 'REFUSED'. */
  matchState(matchId: number): 'NONE' | 'PENDING' | 'VALIDATED' | 'REFUSED' {
    const d = this.demandesByMatchId[matchId];
    if (!d) return 'NONE';
    if (d.statut === 'VALIDE') return 'VALIDATED';
    if (d.statut === 'REFUSE') return 'REFUSED';
    return 'PENDING';
  }

  matchDemande(matchId: number): any {
    return this.demandesByMatchId[matchId] || null;
  }

  demanderAccreditation(match: any) {
    if (!this.hasPhoto()) {
      this.toast.show('error', 'Téléversez d\'abord votre photo de profil (onglet Mon badge).');
      return;
    }
    this.busyMatchId = match.id;
    this.api.createPressAccreditation(match.id).subscribe({
      next: (resp) => {
        this.busyMatchId = null;
        this.toast.show('success', 'Demande envoyée. En attente de validation par le club.');
        // Met à jour la liste locale
        if (resp) this.demandesByMatchId[match.id] = resp;
        this.api.getMyPressAccreditations().subscribe({
          next: (list) => {
            this.mesDemandes = list || [];
            this.demandesByMatchId = {};
            for (const d of this.mesDemandes) {
              if (d && d.matchId != null) this.demandesByMatchId[d.matchId] = d;
            }
          }
        });
      },
      error: (err) => {
        this.busyMatchId = null;
        const code = err?.error?.error || err?.error?.code;
        let msg = 'Échec de l\'envoi de la demande.';
        if (code === 'MATCH_NOT_FOUND') msg = 'Ce match n\'existe plus dans le calendrier.';
        else if (code === 'DUPLICATE_ACCREDITATION') msg = 'Vous avez déjà une demande pour ce match.';
        else if (code === 'PHOTO_REQUIRED') msg = 'Photo de profil obligatoire.';
        else if (err?.error?.message) msg = err.error.message;
        this.toast.show('error', msg);
      }
    });
  }

  telechargerBadge(accreditationId: number) {
    this.api.getPressAccreditationBadge(accreditationId).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `badge-accreditation-${accreditationId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.toast.show('success', 'Badge téléchargé.');
      },
      error: (err) => {
        this.toast.show('error', err?.error?.message || 'Badge indisponible.');
      }
    });
  }

  // ───────────────────── Formattage ─────────────────────

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
