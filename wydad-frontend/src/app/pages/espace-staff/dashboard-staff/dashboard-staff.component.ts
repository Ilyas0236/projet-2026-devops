import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-dashboard-staff',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './dashboard-staff.component.html'
})
export class DashboardStaffComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  fb = inject(FormBuilder);
  toast = inject(ToastService);

  staff: any = null;
  players: any[] = [];
  sessions: any[] = [];
  loading = true;
  staffNotFound = false;

  sessionForm!: FormGroup;
  isSubmitting = false;
  showForm = false;

  ngOnInit() {
    this.sessionForm = this.fb.group({
      title: ['', Validators.required],
      description: [''],
      location: ['Complexe Benjelloun', Validators.required],
      sessionDate: ['', Validators.required]
    });

    // Charger le profil staff depuis le backend via l'ID utilisateur connecté
    const userId = this.auth.getCurrentUserId();
    if (userId) {
      this.api.getStaffByUserId(userId).subscribe({
        next: (data) => {
          this.staff = data;
          this.loadDashboardData();
        },
        error: (err) => {
          console.error('Profil staff non trouvé', err);
          this.staffNotFound = true;
          this.loading = false;
        }
      });
    } else {
      this.staffNotFound = true;
      this.loading = false;
    }
  }

  loadDashboardData() {
    const sport = this.staff.sportType;
    const category = this.staff.assignedCategory || this.staff.category;

    this.api.getPlayersByCategory(sport, category).subscribe({
      next: (data) => {
        this.players = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });

    this.api.getSessionsByCategory(sport, category).subscribe({
      next: (data) => {
        this.sessions = data;
      },
      error: (err) => console.error(err)
    });
  }

  submitSession() {
    if (this.sessionForm.invalid) return;
    this.isSubmitting = true;

    const sport = this.staff.sportType;
    const category = this.staff.assignedCategory || this.staff.category;

    const payload = {
      ...this.sessionForm.value,
      sportType: sport,
      category: category,
      createdByStaffId: this.staff.id
    };

    this.api.createSession(payload).subscribe({
      next: (res) => {
        this.sessions.push(res);
        this.sessions.sort((a, b) => new Date(a.sessionDate).getTime() - new Date(b.sessionDate).getTime());
        this.isSubmitting = false;
        this.showForm = false;
        this.sessionForm.reset({ location: 'Complexe Benjelloun' });
      },
      error: (err) => {
        console.error(err);
        this.isSubmitting = false;
        this.toast.error('Erreur lors de la création de la séance');
      }
    });
  }
}
