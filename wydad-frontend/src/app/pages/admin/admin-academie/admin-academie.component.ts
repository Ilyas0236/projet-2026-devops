import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/** Libellés lisibles des types de pièces justificatives. */
const DOC_LABELS: Record<string, string> = {
  BIRTH_CERTIFICATE: 'Extrait de naissance',
  MEDICAL_CERTIFICATE: 'Certificat médical',
  PHOTO: 'Photo'
};

/**
 * Back-office Académie (STAFF/ADMIN) : liste globale des dossiers
 * d'inscription, consultation des pièces justificatives réellement
 * transmises au backend (0-BIS.6) et validation/rejet d'un dossier.
 */
@Component({
  selector: 'app-admin-academie',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Académie — Dossiers d'inscription</h2>
        <p class="text-sm text-gray-400 mt-1">
          Consultez les pièces justificatives transmises par les parents avant de valider un dossier.
        </p>
      </div>

      <!-- LOADING -->
      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <!-- ERROR -->
      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <!-- EMPTY -->
      <div *ngIf="!loading && folders.length === 0" class="text-center py-16 text-gray-500">
        Aucun dossier d'inscription pour le moment.
      </div>

      <!-- FOLDERS -->
      <div *ngIf="!loading && folders.length > 0"
           class="bg-white/[0.03] border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-sm min-w-[900px]">
          <thead>
            <tr class="text-left text-[10px] uppercase tracking-widest text-gray-400 border-b border-white/10">
              <th class="px-4 py-3">Enfant</th>
              <th class="px-4 py-3">Sport / Niveau</th>
              <th class="px-4 py-3">Contact d'urgence</th>
              <th class="px-4 py-3">Pièces</th>
              <th class="px-4 py-3">Statut</th>
              <th class="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            <ng-container *ngFor="let folder of folders">
              <tr class="border-b border-white/[0.04] hover:bg-white/[0.02] transition-colors">
                <td class="px-4 py-3">
                  <p class="text-white font-semibold">{{ folder.childFullName }}</p>
                  <p class="text-xs text-gray-500">Né(e) le {{ folder.childBirthDate | date:'dd/MM/yyyy' }}</p>
                </td>
                <td class="px-4 py-3 text-gray-300">{{ folder.sportType }} — {{ folder.level }}</td>
                <td class="px-4 py-3 text-gray-300">
                  {{ folder.emergencyContactName || '—' }}
                  <span *ngIf="folder.emergencyContactPhone" class="block text-xs text-gray-500">{{ folder.emergencyContactPhone }}</span>
                </td>
                <td class="px-4 py-3">
                  <button (click)="toggleDocuments(folder)"
                          class="text-blue-400 hover:text-blue-300 uppercase font-bold text-xs"
                          [disabled]="docsLoadingId === folder.id">
                    {{ docsLoadingId === folder.id ? '...' : (expandedId === folder.id ? 'Masquer' : 'Consulter') }}
                  </button>
                </td>
                <td class="px-4 py-3">
                  <span *ngIf="folder.active"
                        class="px-2 py-1 bg-green-500/20 border border-green-500/50 text-green-400 text-[10px] font-bold rounded-full uppercase tracking-widest">Validé</span>
                  <span *ngIf="!folder.active"
                        class="px-2 py-1 bg-yellow-500/20 border border-yellow-500/50 text-yellow-400 text-[10px] font-bold rounded-full uppercase tracking-widest">En attente</span>
                </td>
                <td class="px-4 py-3 text-right space-x-2 whitespace-nowrap">
                  <button *ngIf="!folder.active" (click)="setStatus(folder, true)" [disabled]="statusLoadingId === folder.id"
                          class="bg-green-600 hover:bg-green-500 disabled:opacity-50 text-white px-3 py-1.5 rounded font-bold text-xs uppercase tracking-wider transition-colors">
                    Valider
                  </button>
                  <button *ngIf="folder.active" (click)="setStatus(folder, false)" [disabled]="statusLoadingId === folder.id"
                          class="bg-surface-2 hover:bg-red-500/20 disabled:opacity-50 text-gray-300 hover:text-red-300 px-3 py-1.5 rounded font-bold text-xs uppercase tracking-wider transition-colors border border-white/10">
                    Suspendre
                  </button>
                </td>
              </tr>
              <!-- Pièces du dossier (ligne dépliable) -->
              <tr *ngIf="expandedId === folder.id">
                <td colspan="6" class="px-4 pb-4 bg-black/20">
                  <div *ngIf="documentsOf(folder.id).length === 0" class="py-3 text-xs text-gray-500">
                    Aucune pièce transmise pour ce dossier.
                  </div>
                  <div *ngIf="documentsOf(folder.id).length > 0" class="flex flex-wrap gap-3 pt-1">
                    <div *ngFor="let doc of documentsOf(folder.id)"
                         class="flex items-center gap-3 bg-white/5 border border-white/10 rounded-lg px-3 py-2">
                      <span>{{ doc.contentType?.startsWith('image/') ? '🖼️' : '📄' }}</span>
                      <div>
                        <p class="text-xs text-white font-semibold">{{ docLabel(doc.docType) }}</p>
                        <p class="text-[11px] text-gray-500">{{ formatSize(doc.size) }} · {{ doc.contentType }}</p>
                      </div>
                      <button (click)="viewDocument(folder.id, doc.docType, doc.fileName)"
                              [disabled]="downloadingKey === folder.id + ':' + doc.docType"
                              class="ml-2 text-blue-400 hover:text-blue-300 text-xs uppercase font-bold disabled:opacity-50">
                        {{ downloadingKey === folder.id + ':' + doc.docType ? 'Chargement...' : 'Ouvrir' }}
                      </button>
                    </div>
                  </div>
                </td>
              </tr>
            </ng-container>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AdminAcademieComponent implements OnInit {
  folders: any[] = [];
  loading = true;
  error = '';

  /** Dossier dont les pièces sont dépliées + cache des métadonnées par dossier. */
  expandedId: number | null = null;
  private docsByFolder: Record<number, any[]> = {};
  docsLoadingId: number | null = null;

  statusLoadingId: number | null = null;
  downloadingKey: string | null = null;

  api = inject(ApiService);
  private toast = inject(ToastService);

  ngOnInit() {
    this.loadFolders();
  }

  loadFolders() {
    this.loading = true;
    this.api.getAllAcademyFolders().subscribe({
      next: (data) => {
        this.folders = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les dossiers.';
        this.loading = false;
      }
    });
  }

  toggleDocuments(folder: any) {
    if (this.expandedId === folder.id) {
      this.expandedId = null;
      return;
    }
    this.expandedId = folder.id;
    if (!this.docsByFolder[folder.id]) {
      this.docsLoadingId = folder.id;
      this.api.getAcademyDocuments(folder.id).subscribe({
        next: (docs) => {
          this.docsByFolder[folder.id] = docs;
          this.docsLoadingId = null;
        },
        error: () => {
          this.toast.error('Impossible de charger les pièces de ce dossier.');
          this.docsLoadingId = null;
          this.expandedId = null;
        }
      });
    }
  }

  documentsOf(folderId: number): any[] {
    return this.docsByFolder[folderId] || [];
  }

  /**
   * Ouverture authentifiée : le blob est récupéré via HttpClient (le JWT
   * est injecté par l'interceptor), puis affiché dans un onglet via un
   * object URL. Un simple href ne porterait pas le token.
   */
  viewDocument(folderId: number, docType: string, fileName: string) {
    const key = folderId + ':' + docType;
    this.downloadingKey = key;
    this.api.getAcademyDocumentBlob(folderId, docType).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
        // Laisser le navigateur charger l'object URL avant révocation.
        setTimeout(() => window.URL.revokeObjectURL(url), 60_000);
        this.downloadingKey = null;
      },
      error: () => {
        this.toast.error('Erreur lors de l\'ouverture du document.');
        this.downloadingKey = null;
      }
    });
  }

  async setStatus(folder: any, active: boolean) {
    this.statusLoadingId = folder.id;
    this.api.updateAcademyStatus(folder.id, active).subscribe({
      next: () => {
        folder.active = active;
        this.toast.success(active
          ? `Dossier de ${folder.childFullName} validé.`
          : `Dossier de ${folder.childFullName} suspendu.`);
        this.statusLoadingId = null;
      },
      error: () => {
        this.toast.error('Erreur lors de la mise à jour du statut.');
        this.statusLoadingId = null;
      }
    });
  }

  docLabel(docType: string): string {
    return DOC_LABELS[docType] || docType;
  }

  formatSize(bytes: number): string {
    if (!bytes && bytes !== 0) return '';
    if (bytes < 1024) return bytes + ' o';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' Ko';
    return (bytes / (1024 * 1024)).toFixed(1) + ' Mo';
  }
}
