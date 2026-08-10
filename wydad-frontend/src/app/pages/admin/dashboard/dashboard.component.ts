import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  stats = {
    users: 0,
    articles: 0,
    matchs: 0,
    notifications: 0
  };
  loading = true;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadStats();
  }

  loadStats() {
    let completed = 0;
    const checkComplete = () => {
      completed++;
      if (completed === 4) this.loading = false;
    };

    this.apiService.getAllUsers().subscribe(data => { this.stats.users = data.length; checkComplete(); }, () => checkComplete());
    this.apiService.getArticles().subscribe(data => { this.stats.articles = data.length; checkComplete(); }, () => checkComplete());
    this.apiService.getMatches().subscribe(data => { this.stats.matchs = data.length; checkComplete(); }, () => checkComplete());
    this.apiService.getAllNotifications().subscribe(data => { this.stats.notifications = data.length; checkComplete(); }, () => checkComplete());
  }
}
