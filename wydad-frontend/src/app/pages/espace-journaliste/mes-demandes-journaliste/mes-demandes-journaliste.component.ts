import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';

/**
 * Page dédiée /journaliste/demandes (B.17) — récapitulatif des demandes
 * d'accréditation presse du journaliste connecté. Vue différente de l'onglet
 * "Mes demandes" du dashboard : page pleine, avec lien retour vers l'espace.
 */
@Component({
  selector: 'app-mes-demandes-journaliste',
  standalone: true,
  imports: [CommonModule, RouterModule, ErrorBannerComponent],
  templateUrl: './mes-demandes-journaliste.component.html'
})
export class MesDemandesJournalisteComponent implements OnInit {
  loading = true;
  demandes: any[] = [];
  busyId: number | null = null;

  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);
  router = inject(Router);

  ngOnInit() {
    this.load();
  }

  retry() { this.load(); }

  private load() {
    this.loading = true;
    this.api.getMyPressAccreditations().subscribe({
      next: (list) => {
        this.demandes = (list || []).slice().sort((a, b) => {
          // Tri : EN_ATTENTE en haut, puis VALIDE, puis REFUSE
          const order: any = { EN_ATTENTE: 0, VALIDE: 1, REFUSE: 2 };
          return (order[a.statut] ?? 9) - (order[b.statut] ?? 9);
        });
        this.loading = false;
      },
      error: () => { this.demandes = []; this.loading = false; }
    });
  }

  telechargerBadge(accreditationId: number) {
    this.busyId = accreditationId;
    this.api.getPressAccreditationBadge(accreditationId).subscribe({
      next: (blob: Blob) => {
        this.busyId = null;
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `badge-accreditation-${accreditationId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.toast.show('success', 'Badge téléchargé.');
      },
      error: (err) => {
        this.busyId = null;
        this.toast.show('error', err?.error?.message || 'Badge indisponible.');
      }
    });
  }

  formatDate(iso: any): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
  }
}
