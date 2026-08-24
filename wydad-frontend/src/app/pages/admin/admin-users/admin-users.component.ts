import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss'
})
export class AdminUsersComponent implements OnInit {
  users: any[] = [];
  filteredUsers: any[] = [];
  loading = true;
  searchTerm = '';
  roleFilter = 'ALL';
  kycFilter = 'ALL';

  constructor(private authService: AuthService,
              private toast: ToastService,
              private confirm: ConfirmService) {}

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.loading = true;
    this.authService.getAllUsers().subscribe({
      next: (data) => {
        this.users = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching users', err);
        this.loading = false;
      }
    });
  }

  applyFilters() {
    this.filteredUsers = this.users.filter(u => {
      const matchSearch = u.email.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
                          u.firstName.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
                          u.lastName.toLowerCase().includes(this.searchTerm.toLowerCase());
      
      const matchRole = this.roleFilter === 'ALL' || u.role === this.roleFilter;
      const matchKyc = this.kycFilter === 'ALL' || 
                       (this.kycFilter === 'VERIFIED' && u.kycVerified) || 
                       (this.kycFilter === 'PENDING' && !u.kycVerified);

      return matchSearch && matchRole && matchKyc;
    });
  }

  toggleStatus(user: any) {
    const newStatus = !user.active;
    this.authService.toggleUserActiveStatus(user.id, newStatus).subscribe({
      next: () => {
        user.active = newStatus;
        this.toast.success(newStatus ? 'Compte activé.' : 'Compte désactivé.');
      },
      error: (err) => {
        console.error('Error toggling status', err);
        this.toast.error('Erreur lors du changement de statut.');
      }
    });
  }

  async changeRole(user: any, newRole: string) {
    const ok = await this.confirm.confirm({
      title: 'Changer le rôle',
      message: `Voulez-vous vraiment changer le rôle de ${user.firstName} en ${newRole} ?`,
      confirmLabel: 'Changer le rôle'
    });
    if (!ok) return;
    this.authService.changeUserRole(user.id, newRole).subscribe({
      next: () => {
        user.role = newRole;
        this.toast.success('Rôle mis à jour.');
      },
      error: (err) => {
        console.error('Error changing role', err);
        this.toast.error('Erreur lors du changement de rôle.');
      }
    });
  }

  // Mock approval for KYC from admin interface
  async approveKyc(user: any) {
    const ok = await this.confirm.confirm({
      title: 'Valider le KYC',
      message: `Approuver le KYC pour ${user.email} ?`,
      confirmLabel: 'Approuver'
    });
    if (!ok) return;
    this.authService.verifyKycMock(user.email).subscribe({
      next: () => {
        user.kycVerified = true;
        this.toast.success('KYC approuvé.');
      },
      error: (err) => {
        console.error('Error approving KYC', err);
        this.toast.error('Erreur lors de l\'approbation du KYC.');
      }
    });
  }

  // ─── Modale détails utilisateur ──────────────────────────────
  selectedUser: any = null;

  openDetails(user: any) {
    this.selectedUser = user;
  }

  closeDetails() {
    this.selectedUser = null;
  }

  /** Libellés lisibles des rôles / niveaux d'adhésion. */
  roleLabel(role: string): string {
    return ({
      VISITEUR: 'Visiteur', ADHERENT: 'Adhérent', PARENT: 'Parent',
      JOUEUR: 'Joueur', STAFF: 'Staff', ADMIN: 'Administrateur'
    } as Record<string, string>)[role] || role || '—';
  }

  levelLabel(level: string): string {
    return ({ ROUGE: 'Rouge', OR: 'Or', DIAMANT: 'Diamant', LEGENDE: 'Légende', JUNIOR: 'Junior' } as Record<string, string>)[level] || level || '—';
  }
}
