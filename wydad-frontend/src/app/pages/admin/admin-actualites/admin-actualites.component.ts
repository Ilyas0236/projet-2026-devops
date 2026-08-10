import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

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
              <td class="py-3 px-4 text-sm text-white font-medium">{{ item.title }}</td>
              <td class="py-3 px-4 text-xs text-gray-400">
                <span class="px-2 py-1 bg-white/10 rounded text-white">{{ item.category }}</span>
              </td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ item.publishedAt | date }}</td>
              <td class="py-3 px-4 text-right">
                <button (click)="openModal(item)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteArticle(item.id)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal -->
      <div *ngIf="showModal" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
        <div class="bg-zinc-900 border border-white/10 p-6 w-full max-w-2xl rounded-lg">
          <h3 class="text-xl font-display font-bold text-white uppercase tracking-wider mb-6">
            {{ isEdit ? 'Modifier l\\'Article' : 'Ajouter un Article' }}
          </h3>
          
          <div class="space-y-4">
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Titre</label>
              <input type="text" [(ngModel)]="currentArticle.title" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Résumé</label>
              <textarea [(ngModel)]="currentArticle.summary" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white h-20"></textarea>
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Contenu (HTML/Markdown)</label>
              <textarea [(ngModel)]="currentArticle.content" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white h-40"></textarea>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs text-gray-400 uppercase mb-1">Catégorie</label>
                <input type="text" [(ngModel)]="currentArticle.category" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
              </div>
              <div>
                <label class="block text-xs text-gray-400 uppercase mb-1">URL de l'image</label>
                <input type="text" [(ngModel)]="currentArticle.imageUrl" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
              </div>
            </div>
          </div>
          
          <div class="mt-8 flex justify-end gap-3">
            <button (click)="closeModal()" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveArticle()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Sauvegarder</button>
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

  constructor(private apiService: ApiService) {}

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
      this.currentArticle = { title: '', summary: '', content: '', category: 'CLUB', imageUrl: '' };
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

  deleteArticle(id: number) {
    if (confirm('Voulez-vous vraiment supprimer cet article ?')) {
      this.apiService.deleteArticle(id).subscribe(() => this.loadArticles());
    }
  }
}
