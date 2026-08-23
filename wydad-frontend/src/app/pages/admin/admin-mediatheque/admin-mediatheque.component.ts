import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Mediatheque du back-office : upload de fichiers (images/PDF), listing
 * des metadonnees et suppression. Les blobs restent cotes content-service.
 */
@Component({
  selector: 'app-admin-mediatheque',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Médiathèque</h2>
          <p class="text-sm text-gray-400 mt-1">Fichiers servis par le content-service. Copiez l'URL pour l'utiliser dans les contenus.</p>
        </div>
        <label class="cursor-pointer bg-wydad-red hover:bg-red-700 text-white px-4 py-2 font-display font-bold uppercase tracking-wider text-sm skew-x-[-10deg] transition-colors">
          <span class="skew-x-[10deg] block">{{ uploading ? 'Envoi...' : 'Téléverser' }}</span>
          <input type="file" accept="image/jpeg,image/png,image/gif,image/webp,application/pdf" (change)="upload($event)" class="hidden" [disabled]="uploading">
        </label>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading && files.length === 0" class="text-center py-16 text-gray-500">
        Aucun fichier téléversé pour le moment.
      </div>

      <div *ngIf="!loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div *ngFor="let f of files" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
          <div class="h-36 bg-black flex items-center justify-center">
            <img *ngIf="isImage(f)" [src]="f.url" [alt]="f.originalName" class="max-h-full object-contain">
            <span *ngIf="!isImage(f)" class="text-4xl">📄</span>
          </div>
          <div class="p-4 space-y-2">
            <p class="text-sm text-white truncate" [title]="f.originalName">{{ f.originalName }}</p>
            <p class="text-xs text-gray-500">{{ formatSize(f.size) }} · {{ f.contentType }} · {{ f.uploadedAt | date:'dd/MM/yyyy' }}</p>
            <div class="flex items-center justify-between pt-1">
              <button (click)="copyUrl(f)" class="text-blue-400 hover:text-blue-300 text-xs uppercase font-bold">Copier URL</button>
              <button (click)="deleteFile(f)" class="text-red-400 hover:text-red-300 text-xs uppercase font-bold">Supprimer</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminMediathequeComponent implements OnInit {
  files: any[] = [];
  loading = true;
  error = '';
  uploading = false;

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadFiles();
  }

  loadFiles() {
    this.loading = true;
    this.api.getMediaLibrary().subscribe({
      next: (data) => {
        this.files = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger la médiathèque.';
        this.loading = false;
      }
    });
  }

  isImage(file: any): boolean {
    return !!file.contentType && file.contentType.startsWith('image/');
  }

  formatSize(bytes: number): string {
    if (!bytes && bytes !== 0) return '';
    if (bytes < 1024) return bytes + ' o';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' Ko';
    return (bytes / (1024 * 1024)).toFixed(1) + ' Mo';
  }

  upload(event: any) {
    const file: File = event.target.files[0];
    if (!file) return;
    this.uploading = true;
    this.api.uploadMedia(file).subscribe({
      next: () => {
        this.toast.success('Fichier téléversé.');
        this.uploading = false;
        event.target.value = '';
        this.loadFiles();
      },
      error: () => {
        this.toast.error('Échec du téléversement (types acceptés : JPEG, PNG, GIF, WebP, PDF).');
        this.uploading = false;
      }
    });
  }

  copyUrl(file: any) {
    const absolute = new URL(file.url, window.location.origin).toString();
    navigator.clipboard.writeText(absolute)
      .then(() => this.toast.success('URL copiée.'))
      .catch(() => this.toast.error('Copie impossible dans ce navigateur.'));
  }

  async deleteFile(file: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le fichier',
      message: `Supprimer « ${file.originalName} » ? Les contenus qui l'utilisent perdront leur image.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteMedia(file.id).subscribe({
      next: () => {
        this.toast.success('Fichier supprimé.');
        this.loadFiles();
      },
      error: () => this.toast.error('Erreur lors de la suppression.')
    });
  }
}
