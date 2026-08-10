import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';

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

  constructor(private authService: AuthService) {}

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
      },
      error: (err) => console.error('Error toggling status', err)
    });
  }

  changeRole(user: any, newRole: string) {
    if(confirm(`Voulez-vous vraiment changer le rôle de ${user.firstName} en ${newRole} ?`)) {
      this.authService.changeUserRole(user.id, newRole).subscribe({
        next: () => {
          user.role = newRole;
        },
        error: (err) => console.error('Error changing role', err)
      });
    }
  }

  // Mock approval for KYC from admin interface
  approveKyc(user: any) {
    if(confirm(`Approuver le KYC pour ${user.email} ?`)) {
      this.authService.verifyKycMock(user.email).subscribe({
        next: () => {
          user.kycVerified = true;
        },
        error: (err) => console.error('Error approving KYC', err)
      });
    }
  }
}
