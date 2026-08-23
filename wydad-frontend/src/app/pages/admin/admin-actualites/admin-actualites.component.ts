import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-actualites',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Gestion des Actualités</h2>
          <p class="text-sm text-gray-400 mt-1">Gérez les articles de presse et les annonces.</p>
        </div>
        <button (click)="openModal()" class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 font-display font-bold uppercase tracking-wider text-sm skew-x-[-10deg] transition-colors">
          <span class="skew-x-[10deg] block">Ajouter un Article</span>
        </button>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="!loading" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10">
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Titre</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Catégorie</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">Date</th>
              <th class="py-3 px-4 text-xs font-display font-bold text-gray-400 uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let item of articles" class="hover:bg-white/5 transition-colors">
              <td class="py-3 px-4 text-sm text-white font-medium">{{ item.titre }}</td>
              <td class="py-3 px-4 text-xs text-gray-400">
                <span class="px-2 py-1 bg-white/10 rounded text-white">{{ item.sport }}</span>
              </td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ item.createdAt | date }}</td>
              <td class="py-3 px-4 text-right">
                <button (click)="openModal(item)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteArticle(item.id)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal -->
      <div *ngIf="showModal" class="admin-overlay">
        <div class="admin-modal max-w-2xl">
          <div class="admin-modal-header">
            <h3>{{ isEdit ? 'Modifier l\'Article' : 'Ajouter un Article' }}</h3>
            <button (click)="closeModal()" class="admin-modal-close" aria-label="Fermer">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>

          <div class="admin-modal-body space-y-5">
            <div class="admin-field">
              <label class="admin-label">Titre<span class="req">*</span></label>
              <input type="text" [(ngModel)]="currentArticle.titre" class="admin-input">
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="admin-field">
                <label class="admin-label">Rubrique</label>
                <select [(ngModel)]="currentArticle.sport" class="admin-input">
                  <option value="GENERAL">Général</option>
                  <option value="FOOTBALL">Football</option>
                  <option value="BASKETBALL">Basketball</option>
                  <option value="HANDBALL">Handball</option>
                  <option value="VOLLEYBALL">Volleyball</option>
                  <option value="NATATION">Natation</option>
                  <option value="JUDO">Judo</option>
                  <option value="ATHLETISME">Athlétisme</option>
                </select>
              </div>
              <div class="admin-field">
                <label class="admin-label">Auteur</label>
                <input type="text" [(ngModel)]="currentArticle.auteur" class="admin-input">
              </div>
            </div>
            <div class="admin-field">
              <label class="admin-label">Contenu (HTML/Markdown)</label>
              <textarea [(ngModel)]="currentArticle.contenu" class="admin-input font-mono !min-h-[10rem]"></textarea>
            </div>
            <div class="admin-field">
              <label class="admin-label">Image de couverture</label>
              <div class="admin-upload-zone">
                <label class="admin-upload-btn">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                  Choisir
                  <input type="file" accept="image/*" (change)="uploadPhoto($event)">
                </label>
                <img *ngIf="currentArticle.imageUrl" [src]="apiService.getMediaUrl(currentArticle.imageUrl)" class="admin-upload-preview h-10 object-contain" alt="preview">
                <span *ngIf="uploadingPhoto" class="admin-upload-status">Envoi en cours...</span>
              </div>
            </div>
          </div>

          <div class="admin-modal-footer">
            <button (click)="closeModal()" class="admin-btn-ghost">Annuler</button>
            <button (click)="saveArticle()" class="admin-btn-primary">Sauvegarder</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminActualitesComponent implements OnInit {
  articles: any[] = [];
  loading = true;
  showModal = false;
  isEdit = false;
  currentArticle: any = {};

  constructor(public apiService: ApiService,
              private toast: ToastService,
              private confirm: ConfirmService) {}

  ngOnInit() {
    this.loadArticles();
  }

  loadArticles() {
    this.loading = true;
    this.apiService.getArticles().subscribe({
      next: (data) => {
        this.articles = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });
  }

  openModal(article?: any) {
    if (article) {
      this.isEdit = true;
      this.currentArticle = { ...article };
    } else {
      this.isEdit = false;
      this.currentArticle = { titre: '', contenu: '', sport: 'GENERAL', auteur: 'Rédaction WAC', imageUrl: '' };
    }
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  saveArticle() {
    if (this.isEdit) {
      this.apiService.updateArticle(this.currentArticle.id, this.currentArticle).subscribe(() => {
        this.loadArticles();
        this.closeModal();
      });
    } else {
      this.apiService.createArticle(this.currentArticle).subscribe(() => {
        this.loadArticles();
        this.closeModal();
      });
    }
  }

  async deleteArticle(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer l\'article',
      message: 'Voulez-vous vraiment supprimer cet article ? Cette action est irréversible.',
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.apiService.deleteArticle(id).subscribe({
      next: () => this.loadArticles(),
      error: () => this.toast.error('Erreur lors de la suppression de l\'article.')
    });
  }

  uploadingPhoto = false;

  uploadPhoto(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    
    this.uploadingPhoto = true;
    this.apiService.uploadMedia(file).subscribe({
      next: (res) => {
        this.currentArticle.imageUrl = res.url;
        this.uploadingPhoto = false;
      },
      error: (err) => {
        console.error('Erreur upload', err);
        this.uploadingPhoto = false;
        this.toast.error('Erreur lors du chargement de la photo.');
      }
    });
  }
}
