import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-dashboard-parent',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard-parent.component.html'
})
export class DashboardParentComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);

  children: any[] = [];
  loading = true;

  ngOnInit() {
    const parentId = this.auth.getCurrentUserId();
    if (parentId) {
      this.api.getAcademyChildrenByParent(parentId).subscribe({
        next: (data) => {
          this.children = data;
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
