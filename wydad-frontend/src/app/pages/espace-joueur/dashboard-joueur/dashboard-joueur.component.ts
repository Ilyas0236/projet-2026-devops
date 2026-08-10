import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-dashboard-joueur',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard-joueur.component.html'
})
export class DashboardJoueurComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);

  player: any = null;
  sessions: any[] = [];
  loading = true;

  ngOnInit() {
    const userId = this.auth.getCurrentUserId();
    if (userId) {
      this.api.getPlayerByUserId(userId).subscribe({
        next: (data) => {
          this.player = data;
          this.loadSessions();
        },
        error: (err) => {
          console.error(err);
          this.loading = false;
        }
      });
    } else {
      this.loading = false;
    }
  }

  loadSessions() {
    if (this.player && this.player.sportType && this.player.category) {
      this.api.getSessionsByCategory(this.player.sportType, this.player.category).subscribe({
        next: (data) => {
          this.sessions = data;
          this.loading = false;
        },
        error: (err) => {
          console.error(err);
          this.loading = false;
        }
      });
    } else {
      this.loading = false;
    }
  }
}
