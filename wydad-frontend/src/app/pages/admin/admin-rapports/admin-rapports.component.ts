import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * ADMIN — publication des rapports financiers : upload du PDF (médiathèque),
 * publication, notification broadcast à tous les adhérents.
 */
@Component({
  selector: 'app-admin-rapports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-rapports.component.html',
  styles: []
})
export class AdminRapportsComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  rapports: any[] = [];
  loading = true;

  // Formulaire de publication
  titre = '';
  annee: number = new Date().getFullYear();
  description = '';
  fileUrl = '';
  originalName = '';
  uploading = false;
  publishing = false;
  notifyingId: number | null = null;
  uploadErr = '';

  // Sélecteur de fichier
  browseFile(fileInput: HTMLInputElement) {
    fileInput.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    this.uploadErr = '';
    if (!file) return;
    if (file.type !== 'application/pdf') {
      this.uploadErr = 'Seuls les fichiers PDF sont acceptés.';
      input.value = '';
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      this.uploadErr = 'Fichier trop volumineux (max. 20 Mo).';
      input.value = '';
      return;
    }
    this.uploading = true;
    this.api.uploadMedia(file).subscribe({
      next: (res) => {
        this.fileUrl = res.url;
        this.originalName = res.originalName || file.name;
        this.uploading = false;
        this.toast.success('PDF téléversé. Complétez puis publiez.');
        input.value = '';
      },
      error: (err: any) => {
        this.uploading = false;
        const status = err?.status;
        let raison = '';
        if (status === 401 || status === 403) {
          raison = ' (session expirée — reconnectez-vous)';
        } else if (status === 413) {
          raison = ' (fichier trop volumineux, max. 20 Mo)';
        } else if (status === 415) {
          raison = ' (fichier refusé : seul un vrai PDF est accepté)';
        }
        this.uploadErr = `Échec du téléversement${raison}.${status ? ` [erreur ${status}]` : ''}`;
        input.value = '';
      }
    });
  }

  canPublish(): boolean {
    return this.titre.trim().length > 0 && this.annee > 1900 && this.fileUrl.length > 0;
  }

  publish() {
    if (!this.canPublish()) return;
    this.publishing = true;
    this.api.publierRapportFinancier({
      titre: this.titre.trim(),
      annee: this.annee,
      description: this.description.trim() || undefined,
      fileUrl: this.fileUrl,
      originalName: this.originalName
    }).subscribe({
      next: () => {
        this.toast.success('Rapport publié.');
        this.resetForm();
        this.publishing = false;
        this.loadRapports();
      },
      error: () => {
        this.toast.error('Erreur lors de la publication.');
        this.publishing = false;
      }
    });
  }

  /** Notifie tous les adhérents actifs (broadcast IN_APP existant). */
  notifyAll(rapport: any) {
    this.notifyingId = rapport.id;
    this.api.broadcastNotification({
      title: '📊 Nouveau rapport financier',
      message: `${rapport.titre} (${rapport.annee}) est disponible dans votre interface et sur la page Transparence.`,
      type: 'IN_APP',
      targetUrl: '/transparence'
    }).subscribe({
      next: (msg: string) => {
        this.toast.success('Adhérents notifiés.');
        this.notifyingId = null;
      },
      error: () => {
        this.toast.error("Erreur lors de la notification des adhérents.");
        this.notifyingId = null;
      }
    });
  }

  async delete(rapport: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le rapport',
      message: `Voulez-vous vraiment supprimer le rapport « ${rapport.titre} » (${rapport.annee}) ?`,
      confirmLabel: 'Supprimer'
    });
    if (!ok) return;
    this.api.supprimerRapportFinancier(rapport.id).subscribe({
      next: () => {
        this.toast.info('Rapport supprimé.');
        this.loadRapports();
      },
      error: () => this.toast.error('Erreur lors de la suppression.')
    });
  }

  resetForm() {
    this.titre = '';
    this.annee = new Date().getFullYear();
    this.description = '';
    this.fileUrl = '';
    this.originalName = '';
    this.uploadErr = '';
  }

  loadRapports() {
    this.loading = true;
    this.api.getRapportsFinanciers().subscribe({
      next: (data: any[]) => {
        this.rapports = Array.isArray(data) ? data : [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Impossible de charger les rapports.');
      }
    });
  }

  ngOnInit() {
    this.loadRapports();
  }
}
