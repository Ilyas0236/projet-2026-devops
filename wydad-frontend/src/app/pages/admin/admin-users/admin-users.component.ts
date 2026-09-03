import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

interface CreateUserForm {
  email: string;
  phone: string;
  password: string;
  firstName: string;
  lastName: string;
  role: string;
  discipline: string;
  categorie: string;
}

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
  /** Filtre sur le statut du compte (EN_ATTENTE / VALIDE / REFUSE / ALL). */
  statutFilter = 'ALL';
  /** Filtre sur l'état actif/désactivé du compte (ALL / ACTIVE / INACTIVE). */
  activeFilter = 'ALL';
  /** Filtre sur la discipline sportive (FOOTBALL / BASKETBALL / ... / ALL). */
  disciplineFilter = 'ALL';

  // ─── Modale détails utilisateur ──────────────────────────────
  selectedUser: any = null;

  // ─── Modale création de compte ───────────────────────────────
  showCreateModal = false;
  createSubmitting = false;
  createForm: CreateUserForm = this.blankCreateForm();
  createErrorMsg = '';

  /** 9 rôles réels (alignés sur l'enum Role back) — le front n'en exposait que 6. */
  readonly ALL_ROLES = [
    'ADHERENT', 'PARENT', 'JOUEUR', 'ENTRAINEUR', 'STAFF',
    'JOURNALISTE', 'PRESIDENT', 'ADMIN'
  ];
  /** Rôles pour lesquels discipline + catégorie sont obligatoires. */
  readonly SPORT_ROLES = ['JOUEUR', 'ENTRAINEUR', 'STAFF'];
  readonly ALL_DISCIPLINES = [
    'FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL',
    'SWIMMING', 'JUDO', 'ATHLETICS', 'AUTRE'
  ];
  readonly ALL_CATEGORIES = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];

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
        this.toast.error('Erreur chargement utilisateurs.');
      }
    });
  }

  applyFilters() {
    const s = this.searchTerm.trim().toLowerCase();
    this.filteredUsers = this.users.filter(u => {
      const matchSearch = !s
        || (u.email ?? '').toLowerCase().includes(s)
        || (u.firstName ?? '').toLowerCase().includes(s)
        || (u.lastName ?? '').toLowerCase().includes(s);
      const matchRole = this.roleFilter === 'ALL' || u.role === this.roleFilter;
      const matchKyc = this.kycFilter === 'ALL' ||
                       (this.kycFilter === 'VERIFIED' && u.kycVerified) ||
                       (this.kycFilter === 'PENDING' && !u.kycVerified);
      const matchStatut = this.statutFilter === 'ALL' || u.statutCompte === this.statutFilter;
      const matchActive = this.activeFilter === 'ALL' ||
                          (this.activeFilter === 'ACTIVE' && u.active) ||
                          (this.activeFilter === 'INACTIVE' && !u.active);
      const matchDiscipline = this.disciplineFilter === 'ALL' ||
                              (u.disciplineDemandee || '') === this.disciplineFilter;
      return matchSearch && matchRole && matchKyc && matchStatut && matchActive && matchDiscipline;
    });
  }

  resetFilters() {
    this.searchTerm = '';
    this.roleFilter = 'ALL';
    this.kycFilter = 'ALL';
    this.statutFilter = 'ALL';
    this.activeFilter = 'ALL';
    this.disciplineFilter = 'ALL';
    this.applyFilters();
  }

  // ───────────────────────── Status / role / KYC ─────────────────────────

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
        this.toast.error("Erreur lors de l'approbation du KYC.");
      }
    });
  }

  // ───────────────────────── Delete (admin only) ─────────────────────────

  async deleteUser(user: any) {
    const isLastAdmin = user.role === 'ADMIN'
      && this.users.filter(u => u.role === 'ADMIN').length <= 1;
    if (isLastAdmin) {
      this.toast.error('Impossible de supprimer le dernier ADMIN. Promouvez d\'abord un autre compte.');
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Supprimer définitivement le compte',
      message: `Cette action est IRRÉVERSIBLE. Toutes les données de ${user.firstName} ${user.lastName} (${user.email}) seront supprimées (sessions actives, fiche KYC, fiche roster côté sports si applicable).\n\nConfirmez la suppression définitive ?`,
      confirmLabel: 'Supprimer définitivement',
      danger: true
    });
    if (!ok) return;
    this.authService.adminDeleteUser(user.id).subscribe({
      next: () => {
        this.toast.success(`Compte #${user.id} supprimé.`);
        this.users = this.users.filter(u => u.id !== user.id);
        this.applyFilters();
        if (this.selectedUser?.id === user.id) this.selectedUser = null;
      },
      error: (err) => {
        console.error('Error deleting user', err);
        // Message d'erreur côté back (ex : "Impossible de supprimer le dernier admin")
        const msg = typeof err?.error === 'string'
          ? err.error
          : (err?.error?.message || 'Erreur lors de la suppression.');
        this.toast.error(msg);
      }
    });
  }

  // ───────────────────────── Create user modal ─────────────────────────

  blankCreateForm(): CreateUserForm {
    return {
      email: '', phone: '', password: '',
      firstName: '', lastName: '',
      role: 'ADHERENT', discipline: '', categorie: ''
    };
  }

  openCreateModal() {
    this.createForm = this.blankCreateForm();
    this.createErrorMsg = '';
    this.createSubmitting = false;
    this.showCreateModal = true;
  }

  closeCreateModal() {
    if (this.createSubmitting) return;
    this.showCreateModal = false;
  }

  isSportRole(): boolean {
    return this.SPORT_ROLES.includes(this.createForm.role);
  }

  async submitCreate() {
    this.createErrorMsg = '';

    // Validation locale minimaliste avant appel back (le back revalide).
    if (!this.createForm.email || !this.createForm.password
        || !this.createForm.firstName || !this.createForm.lastName) {
      this.createErrorMsg = 'Email, mot de passe, prénom et nom sont obligatoires.';
      return;
    }
    if (this.createForm.password.length < 6) {
      this.createErrorMsg = 'Le mot de passe doit faire au moins 6 caractères.';
      return;
    }
    if (this.isSportRole()) {
      if (!this.createForm.discipline || !this.createForm.categorie) {
        this.createErrorMsg = 'Pour un compte '
          + this.createForm.role
          + ', la discipline ET la catégorie sont obligatoires.';
        return;
      }
    }

    const payload: any = {
      email: this.createForm.email.trim(),
      phone: this.createForm.phone?.trim() || null,
      password: this.createForm.password,
      firstName: this.createForm.firstName.trim(),
      lastName: this.createForm.lastName.trim(),
      role: this.createForm.role
    };
    if (this.isSportRole()) {
      payload.discipline = this.createForm.discipline;
      payload.categorie = this.createForm.categorie;
    }

    this.createSubmitting = true;
    this.authService.adminCreateUser(payload).subscribe({
      next: (created) => {
        this.toast.success(`Compte créé (#${created?.id ?? '?'} — ${payload.email}).`);
        this.showCreateModal = false;
        this.createSubmitting = false;
        this.loadUsers();
      },
      error: (err) => {
        console.error('Error creating user', err);
        const msg = typeof err?.error === 'string'
          ? err.error
          : (err?.error?.message || err?.error?.error || 'Erreur lors de la création.');
        this.createErrorMsg = msg;
        this.createSubmitting = false;
      }
    });
  }

  // ───────────────────────── Details modal ─────────────────────────

  openDetails(user: any) {
    this.selectedUser = user;
  }

  closeDetails() {
    this.selectedUser = null;
  }

  // ───────────────────────── Labels ─────────────────────────

  /** Libellés lisibles des 9 rôles (alignés enum Role back). */
  roleLabel(role: string): string {
    return ({
      VISITEUR: 'Visiteur', ADHERENT: 'Adhérent', PARENT: 'Parent',
      JOUEUR: 'Joueur', ENTRAINEUR: 'Entraîneur', STAFF: 'Staff',
      JOURNALISTE: 'Journaliste', PRESIDENT: 'Président de section',
      ADMIN: 'Administrateur'
    } as Record<string, string>)[role] || role || '—';
  }

  levelLabel(level: string): string {
    return ({ ROUGE: 'Rouge', OR: 'Or', DIAMANT: 'Diamant', LEGENDE: 'Légende', JUNIOR: 'Junior' } as Record<string, string>)[level] || level || '—';
  }

  statutLabel(statut: string): string {
    return ({
      VALIDE: 'Validé',
      EN_ATTENTE: 'En attente',
      REFUSE: 'Refusé'
    } as Record<string, string>)[statut] || statut || '—';
  }

  statutBadgeClass(statut: string): string {
    return ({
      VALIDE: 'bg-green-100 text-green-800 border-green-300',
      EN_ATTENTE: 'bg-amber-100 text-amber-800 border-amber-300',
      REFUSE: 'bg-red-100 text-red-800 border-red-300'
    } as Record<string, string>)[statut] || 'bg-gray-100 text-gray-700 border-gray-300';
  }
}
