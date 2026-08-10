import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';

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

  staff: any = null;
  players: any[] = [];
  sessions: any[] = [];
  loading = true;

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

    // In a real app, we'd have a getStaffByUserId endpoint.
    // For this MVP, we can simulate fetching staff data or just fetch all players for a specific category if we know it.
    // Since we don't have getStaffByUserId implemented in the backend yet, let's just fetch SENIOR FOOTBALL players for now as a mock staff context.
    
    // Mocking staff context
    this.staff = {
      fullName: 'Coach Principal',
      role: 'Entraîneur',
      sportType: 'FOOTBALL',
      category: 'SENIOR'
    };

    this.loadDashboardData();
  }

  loadDashboardData() {
    this.api.getPlayersByCategory(this.staff.sportType, this.staff.category).subscribe({
      next: (data) => {
        this.players = data;
        this.loading = false;
      },
      error: (err) => console.error(err)
    });

    this.api.getSessionsByCategory(this.staff.sportType, this.staff.category).subscribe({
      next: (data) => {
        this.sessions = data;
      },
      error: (err) => console.error(err)
    });
  }

  submitSession() {
    if (this.sessionForm.invalid) return;
    this.isSubmitting = true;

    const payload = {
      ...this.sessionForm.value,
      sportType: this.staff.sportType,
      category: this.staff.category,
      createdByStaffId: 1 // Mock staff ID
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
        alert('Erreur lors de la création de la séance');
      }
    });
  }
}
