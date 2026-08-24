import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';

/**
 * Phase 0 — écran admin « Demandes de comptes » : liste des comptes
 * EN_ATTENTE (ENTRAINEUR / JOURNALISTE / PRESIDENT) avec validation ou
 * refus motivé.
 */
@Component({
  selector: 'app-admin-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-demandes.component.html',
  styles: []
})
export class AdminDemandesComponent implements OnInit {
  demandes: any[] = [];
  loading = true;
  error = '';

  // Refus
  refuseTarget: any = null;
  motif = '';
  refusing = false;

  // Phase 1 bis — consultation du justificatif (URL signée Cloudinary)
  kycView: any = null;
  kycLoadingEmail = '';
  kycError = '';

  constructor(
    private http: HttpClient,
    private auth: AuthService,
    private toast: ToastService
  ) {}

  ngOnInit() {
    this.loadDemandes();
  }

  loadDemandes() {
    this.loading = true;
    this.http.get<any[]>(`${environment.apiBaseUrl}/auth/admin/accounts/pending`)
      .subscribe({
        next: (data) => {
          this.demandes = Array.isArray(data) ? data : [];
          this.loading = false;
        },
        error: () => {
          this.error = "Impossible de charger les demandes de comptes.";
          this.loading = false;
        }
      });
  }

  validate(d: any) {
    this.http.patch(`${environment.apiBaseUrl}/auth/admin/accounts/${d.id}/validate`, {}, { responseType: 'text' as 'json' })
      .subscribe({
        next: () => {
          this.toast.success(`Compte ${d.email} validé.`);
          this.loadDemandes();
        },
        error: () => this.toast.error("Erreur lors de la validation du compte.")
      });
  }

  openRefuse(d: any) {
    this.refuseTarget = d;
    this.motif = '';
  }

  /**
   * Phase 1 bis — charge l'URL signée du justificatif du demandeur et
   * l'ouvre dans un nouvel onglet. L'URL expire au bout d'une heure.
   */
  viewJustificatif(d: any) {
    this.kycLoadingEmail = d.email;
    this.kycError = '';
    this.http.get<any>(`${environment.apiBaseUrl}/auth/admin/kyc/document`, {
      params: { email: d.email }
    }).subscribe({
      next: (res) => {
        this.kycLoadingEmail = '';
        if (res?.documentUrl) {
          window.open(res.documentUrl, '_blank', 'noopener');
          this.toast.info(`Justificatif de ${d.email} ouvert dans un nouvel onglet (lien valable 1 h).`);
        } else {
          // Mode dégradé : pas d'URL Cloudinary, on affiche les métadonnées.
          this.kycView = res;
          this.toast.info(`Aucun fichier Cloudinary pour ${d.email} — métadonnées affichées ci-dessous.`);
        }
      },
      error: (err) => {
        this.kycLoadingEmail = '';
        this.kycError = err.error?.message || `Aucun justificatif trouvé pour ${d.email}.`;
        setTimeout(() => (this.kycError = ''), 5000);
      }
    });
  }

  closeRefuse() {
    this.refuseTarget = null;
    this.motif = '';
  }

  confirmRefuse() {
    if (!this.motif.trim()) return;
    this.refusing = true;
    this.http.patch(`${environment.apiBaseUrl}/auth/admin/accounts/${this.refuseTarget.id}/refuse`,
        JSON.stringify(this.motif.trim()),
        { headers: { 'Content-Type': 'application/json' }, responseType: 'text' as 'json' })
      .subscribe({
        next: () => {
          this.toast.info(`Compte ${this.refuseTarget.email} refusé.`);
          this.closeRefuse();
          this.refusing = false;
          this.loadDemandes();
        },
        error: () => {
          this.toast.error("Erreur lors du refus du compte.");
          this.refusing = false;
        }
      });
  }
}
