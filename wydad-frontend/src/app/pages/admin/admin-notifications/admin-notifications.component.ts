import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-admin-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-notifications.component.html'
})
export class AdminNotificationsComponent implements OnInit {
  notifications: any[] = [];
  loading = true;
  showModal = false;
  isBroadcast = false;
  currentNotif: any = { type: 'IN_APP', title: '', message: '', targetUrl: '', imageUrl: '' };

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadNotifications();
  }

  loadNotifications() {
    this.loading = true;
    this.apiService.getAllNotifications().subscribe({
      next: (data) => {
        this.notifications = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });
  }

  openModal(broadcast: boolean) {
    this.isBroadcast = broadcast;
    this.currentNotif = { type: 'IN_APP', title: '', message: '', targetUrl: '', imageUrl: '' };
    if (!broadcast) {
      this.currentNotif.userId = null;
    }
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  sendNotification() {
    if (this.isBroadcast) {
      this.apiService.broadcastNotification(this.currentNotif).subscribe(() => {
        alert('Broadcast planifié !');
        this.loadNotifications();
        this.closeModal();
      });
    } else {
      this.apiService.sendNotification(this.currentNotif).subscribe(() => {
        this.loadNotifications();
        this.closeModal();
      });
    }
  }
}
