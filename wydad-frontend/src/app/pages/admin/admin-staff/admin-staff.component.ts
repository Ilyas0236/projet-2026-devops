import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Gestion des profils staff (entraineurs, preparateurs...) par l'ADMIN.
 * Un profil staff est rattache a un compte utilisateur (userId) et une
 * equipe (sport + categorie). Les staff gerent leurs seances depuis
 * /staff/dashboard.
 */
@Component({
  selector: 'app-admin-staff',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Gestion du Staff</h2>
          <p class="text-sm text-gray-400 mt-1">Entraineurs et encadrants rattachés à leurs équipes (sport + catégorie).</p>
        </div>
        <button (click)="openModal()" class="bg-wydad-red hover:bg-red-700 text-white px-4 py-2 font-display font-bold uppercase tracking-wider text-sm skew-x-[-10deg] transition-colors">
          <span class="skew-x-[10deg] block">Nouveau profil</span>
        </button>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="py-3 px-4">Nom</th>
              <th class="py-3 px-4">Rôle</th>
              <th class="py-3 px-4">ID utilisateur</th>
              <th class="py-3 px-4">Sport</th>
              <th class="py-3 px-4">Catégorie</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let s of staff" class="hover:bg-white/5 transition-colors">
              <td class="py-3 px-4 text-sm text-white font-medium">{{ s.fullName }}</td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ s.role }}</td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ s.userId }}</td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ s.sportType }}</td>
              <td class="py-3 px-4 text-sm text-gray-400">{{ s.assignedCategory }}</td>
              <td class="py-3 px-4 text-right">
                <button (click)="openModal(s)" class="text-blue-400 hover:text-blue-300 mx-2 text-xs uppercase font-bold">Éditer</button>
                <button (click)="deleteStaff(s)" class="text-red-400 hover:text-red-300 mx-2 text-xs uppercase font-bold">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Modal create/update -->
      <div *ngIf="showModal" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
        <div class="bg-zinc-900 border border-white/10 p-6 w-full max-w-lg rounded-lg">
          <h3 class="text-xl font-display font-bold text-white uppercase tracking-wider mb-6">
            {{ isEdit ? 'Modifier le profil' : 'Nouveau profil staff' }}
          </h3>
          <div class="grid grid-cols-2 gap-4 mb-4">
            <div class="col-span-2">
              <label class="block text-xs text-gray-400 uppercase mb-1">Nom complet *</label>
              <input type="text" [(ngModel)]="current.fullName" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">ID utilisateur (compte) *</label>
              <input type="number" [(ngModel)]="current.userId" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Rôle</label>
              <input type="text" [(ngModel)]="current.role" placeholder="Entraineur principal..." class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
            </div>
            <div>
              <label class="block text-xs text-gray-400 uppercase mb-1">Sport</label>
              <select [(ngModel)]="current.sportType" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
                <option value="FOOTBALL">Football</option>
                <option value="BASKETBALL">Basketball</option>
                <option value="HANDBALL">Handball</option>
                <option value="VOLLEYBALL">Volleyball</option>
                <option value="NATATION">Natation</option>
                <option value="JUDO">Judo</option>
                <option value="ATHLETISME">Athlétisme</option>
                <option value="GENERAL">Général</option>
              </select>
            </div>
            <div>
              <!-- Valeurs de l'enum Category du sports-service -->
              <label class="block text-xs text-gray-400 uppercase mb-1">Catégorie</label>
              <select [(ngModel)]="current.assignedCategory" class="w-full bg-black border border-white/10 rounded px-3 py-2 text-white">
                <option value="PRO">Pro</option>
                <option value="ESPOIR">Espoir</option>
                <option value="U19">U19</option>
                <option value="U17">U17</option>
                <option value="U15">U15</option>
                <option value="ACADEMY">Académie</option>
              </select>
            </div>
          </div>
          <p *ngIf="formError" class="text-xs text-red-400 mb-3">{{ formError }}</p>
          <div class="mt-8 flex justify-end gap-3">
            <button (click)="closeModal()" class="px-4 py-2 text-gray-400 hover:text-white uppercase text-sm font-bold">Annuler</button>
            <button (click)="saveStaff()" class="px-4 py-2 bg-wydad-red text-white uppercase text-sm font-bold">Sauvegarder</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminStaffComponent implements OnInit {
  staff: any[] = [];
  loading = true;
  error = '';
  showModal = false;
  isEdit = false;
  editingId: number | null = null;
  current: any = {};
  formError = '';

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadStaff();
  }

  loadStaff() {
    this.loading = true;
    this.api.getAllStaff().subscribe({
      next: (data: any[]) => {
        this.staff = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger le staff.';
        this.loading = false;
      }
    });
  }

  openModal(staffMember?: any) {
    if (staffMember) {
      this.isEdit = true;
      this.editingId = staffMember.id;
      this.current = { ...staffMember };
    } else {
      this.isEdit = false;
      this.editingId = null;
      this.current = { fullName: '', userId: null, role: '', sportType: 'FOOTBALL', assignedCategory: 'PRO' };
    }
    this.formError = '';
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  saveStaff() {
    if (!this.current.fullName?.trim()) {
      this.formError = 'Le nom complet est requis.';
      return;
    }
    if (!this.current.userId) {
      this.formError = "L'ID utilisateur (compte) est requis.";
      return;
    }
    if (this.isEdit && this.editingId !== null) {
      this.api.updateStaff(this.editingId, this.current).subscribe({
        next: () => {
          this.toast.success('Profil staff mis à jour.');
          this.loadStaff();
          this.closeModal();
        },
        error: () => this.toast.error('Erreur lors de la mise à jour.')
      });
    } else {
      this.api.createStaff(this.current).subscribe({
        next: () => {
          this.toast.success('Profil staff créé.');
          this.loadStaff();
          this.closeModal();
        },
        error: () => this.toast.error('Erreur lors de la création.')
      });
    }
  }

  async deleteStaff(staffMember: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le profil staff',
      message: `Supprimer le profil de « ${staffMember.fullName} » ?`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteStaff(staffMember.id).subscribe({
      next: () => {
        this.toast.success('Profil staff supprimé.');
        this.loadStaff();
      },
      error: () => this.toast.error('Erreur lors de la suppression.')
    });
  }
}
