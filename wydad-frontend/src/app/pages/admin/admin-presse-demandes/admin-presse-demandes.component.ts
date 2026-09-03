import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';

/**
 * Admin — Accréditations presse (B.17).
 *
 * File d'attente des demandes d'accréditation EN_ATTENTE. L'admin peut
 *   - valider → badge PDF auto-généré + notif journaliste
 *   - refuser avec motif obligatoire → notif journaliste avec motif visible
 *
 * Le composant est volontairement proche de `AdminDemandesComponent` (validation
 * de comptes) : même UX (toast, modal de refus), juste une autre file d'attente.
 */
@Component({
  selector: 'app-admin-presse-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent],
  templateUrl: './admin-presse-demandes.component.html'
})
export class AdminPresseDemandesComponent implements OnInit {
  loading = true;
  demandes: any[] = [];
  /** Historique complet (tous statuts) pour traçabilité admin. Chargé en
   *  parallèle de la file "en attente", affiché via l'onglet "Historique". */
  historique: any[] = [];
  loadingHistorique = false;
  /** Onglet actif : false = En attente (défaut), true = Historique. */
  showHistorique = false;

  busyId: number | null = null;

  refuseTarget: any = null;
  motif = '';
  refusing = false;

  api = inject(ApiService);
  toast = inject(ToastService);

  ngOnInit() {
    this.load();
    this.loadHistory();
  }

  retry() { this.load(); }

  /** Charge l'historique complet (traçabilité : tous statuts confondus). */
  loadHistory() {
    this.loadingHistorique = true;
    this.api.adminGetPressAccreditationHistory().subscribe({
      next: (list) => {
        this.historique = list || [];
        this.loadingHistorique = false;
      },
      error: (err) => {
        console.error('Error fetching accreditation history', err);
        this.historique = [];
        this.loadingHistorique = false;
        this.toast.show('error', 'Erreur lors du chargement de l\'historique des accréditations.');
      }
    });
  }

  /**
   * Bascule entre l'onglet "En attente" et "Historique". L'historique est
   * pré-chargé dans ngOnInit donc le switch est instantané ; on force un
   * refresh au cas où l'admin a validé/refusé une demande depuis.
   */
  toggleHistory() {
    this.showHistorique = !this.showHistorique;
    if (this.showHistorique) {
      this.loadHistory();
    }
  }

  /** Libellé français des 3 statuts d'accréditation. */
  statutLabel(statut: string): string {
    return ({
      VALIDE: 'Validée',
      EN_ATTENTE: 'En attente',
      REFUSE: 'Refusée'
    } as Record<string, string>)[statut] || statut || '—';
  }

  /** Classes Tailwind du badge statut (cohérent avec admin-users). */
  statutBadgeClass(statut: string): string {
    return ({
      VALIDE: 'bg-green-100 text-green-800 border-green-300',
      EN_ATTENTE: 'bg-amber-100 text-amber-800 border-amber-300',
      REFUSE: 'bg-red-100 text-red-800 border-red-300'
    } as Record<string, string>)[statut] || 'bg-gray-100 text-gray-700 border-gray-300';
  }

  load() {
    this.loading = true;
    this.api.adminGetPendingPressAccreditations().subscribe({
      next: (list) => {
        this.demandes = list || [];
        this.loading = false;
      },
      error: () => {
        this.demandes = [];
        this.loading = false;
      }
    });
  }

  valider(d: any) {
    if (this.busyId) return;
    if (!confirm(`Valider la demande de ${d.userFirstName || ''} ${d.userLastName || ''} pour « ${d.matchLabel} » ?\nUn badge PDF sera généré et envoyé au journaliste.`)) return;
    this.busyId = d.id;
    this.api.adminValidatePressAccreditation(d.id).subscribe({
      next: () => {
        this.busyId = null;
        this.toast.show('success', 'Accréditation validée. Badge PDF généré.');
        this.demandes = this.demandes.filter(x => x.id !== d.id);
      },
      error: (err) => {
        this.busyId = null;
        this.toast.show('error', err?.error?.message || 'Échec de la validation.');
      }
    });
  }

  ouvrirRefus(d: any) {
    this.refuseTarget = d;
    this.motif = '';
  }

  annulerRefus() {
    this.refuseTarget = null;
    this.motif = '';
    this.refusing = false;
  }

  confirmerRefus() {
    if (!this.refuseTarget) return;
    if (!this.motif || this.motif.trim().length < 3) {
      this.toast.show('error', 'Le motif de refus est obligatoire (au moins 3 caractères).');
      return;
    }
    this.refusing = true;
    this.api.adminRefusePressAccreditation(this.refuseTarget.id, this.motif.trim()).subscribe({
      next: () => {
        this.refusing = false;
        this.toast.show('success', 'Demande refusée. Le journaliste a été notifié.');
        this.demandes = this.demandes.filter(x => x.id !== this.refuseTarget.id);
        this.annulerRefus();
      },
      error: (err) => {
        this.refusing = false;
        this.toast.show('error', err?.error?.message || 'Échec du refus.');
      }
    });
  }

  formatDate(iso: any): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
}
