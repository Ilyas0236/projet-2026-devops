import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-billetterie',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-billetterie.component.html'
})
export class AdminBilletterieComponent implements OnInit {
  events: any[] = [];
  loading = true;
  showModal = false;
  
  newEvent = {
    title: '',
    homeTeam: 'Wydad AC',
    awayTeam: '',
    eventDate: '',
    competition: 'Botola Pro',
    venue: 'Stade Mohammed V',
    eventType: 'FOOTBALL',
    basePrice: 50,
    totalCapacity: 45000,
    sections: []
  };

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadEvents();
  }

  loadEvents() {
    this.loading = true;
    this.api.getEvents().subscribe({
      next: (data) => {
        this.events = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement events', err);
        this.loading = false;
      }
    });
  }

  openAddModal() {
    this.newEvent = {
      title: '',
      homeTeam: 'Wydad AC',
      awayTeam: '',
      eventDate: '',
      competition: 'Botola Pro',
      venue: 'Stade Mohammed V',
      eventType: 'FOOTBALL',
      basePrice: 50,
      totalCapacity: 45000,
      sections: []
    };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  saveEvent() {
    this.newEvent.title = `${this.newEvent.homeTeam} vs ${this.newEvent.awayTeam}`;
    this.api.createEvent(this.newEvent).subscribe({
      next: (res) => {
        this.loadEvents();
        this.closeAddModal();
      },
      error: (err) => {
        console.error('Erreur création event', err);
        this.toast.error(err.error?.message || 'Erreur lors de la création du match.');
      }
    });
  }

  async deleteEvent(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Annuler le match',
      message: 'Êtes-vous sûr de vouloir annuler ce match ? Les billets vendus seront impactés.',
      confirmLabel: 'Annuler le match',
      danger: true
    });
    if (!ok) return;
    this.api.deleteEvent(id).subscribe({
      next: () => this.loadEvents(),
      error: (err) => console.error('Erreur suppression', err)
    });
  }
}
