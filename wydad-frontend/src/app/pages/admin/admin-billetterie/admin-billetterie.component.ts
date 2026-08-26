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
  competitions: any[] = [];
  loading = true;
  showModal = false;
  isEdit = false;
  editingId: number | null = null;

  emptyEvent() {
    const comp = this.competitions.find(c => c.sport === 'FOOTBALL');
    return {
      title: '',
      homeTeam: 'Wydad AC',
      awayTeam: '',
      eventDate: '',
      competition: comp?.name || '',
      venue: 'Stade Mohammed V',
      eventType: 'FOOTBALL',
      category: 'SENIOR',
      adversaireLogoUrl: '',
      basePrice: 50,
      totalCapacity: 45000,
      sections: []
    };
  }

  newEvent: any = this.emptyEvent();

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    // Competitions dynamiques : parametre club 'competitions' (source ADMIN)
    this.api.getCompetitions().subscribe({
      next: (data) => this.competitions = Array.isArray(data) ? data : [],
      error: () => this.competitions = []
    });
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
    this.isEdit = false;
    this.editingId = null;
    this.newEvent = this.emptyEvent();
    this.showModal = true;
  }

  openEditModal(event: any) {
    this.isEdit = true;
    this.editingId = event.id;
    this.newEvent = {
      title: event.title,
      homeTeam: event.homeTeam,
      awayTeam: event.awayTeam,
      eventDate: event.eventDate ? String(event.eventDate).slice(0, 16) : '',
      competition: event.competition,
      venue: event.venue,
      eventType: event.eventType || 'FOOTBALL',
      category: event.category || 'SENIOR',
      adversaireLogoUrl: event.adversaireLogoUrl || '',
      basePrice: event.basePrice,
      totalCapacity: event.totalCapacity,
      sections: event.sections || []
    };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  // §16/§21 — logo adverse : image téléversée par l'ADMIN à la création du
  // match, reprise automatiquement sur les billets PDF.
  uploadingLogo = false;

  uploadLogo(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    this.uploadingLogo = true;
    this.api.uploadMedia(file).subscribe({
      next: (res) => {
        this.newEvent.adversaireLogoUrl = res.url;
        this.uploadingLogo = false;
      },
      error: () => {
        this.uploadingLogo = false;
        this.toast.error('Erreur lors de l\'upload du logo.');
      }
    });
  }

  /** Disciplines proposées à la création d'un match. */
  readonly sports = ['FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL', 'SWIMMING', 'JUDO', 'ATHLETICS'];
  /** Catégories §26 communes à toutes les disciplines. */
  readonly categories = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];

  saveEvent() {
    if (this.isEdit && this.editingId !== null) {
      this.api.updateEvent(this.editingId, this.newEvent).subscribe({
        next: () => {
          this.toast.success('Match mis à jour.');
          this.loadEvents();
          this.closeAddModal();
        },
        error: (err) => {
          console.error('Erreur modification event', err);
          this.toast.error(err.error?.message || 'Erreur lors de la modification du match.');
        }
      });
    } else {
      this.newEvent.title = `${this.newEvent.homeTeam} vs ${this.newEvent.awayTeam}`;
      this.api.createEvent(this.newEvent).subscribe({
        next: (res) => {
          this.toast.success('Match programmé.');
          this.loadEvents();
          this.closeAddModal();
        },
        error: (err) => {
          console.error('Erreur création event', err);
          this.toast.error(err.error?.message || 'Erreur lors de la création du match.');
        }
      });
    }
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
      next: () => {
        this.toast.success('Match annulé.');
        this.loadEvents();
      },
      error: (err) => {
        console.error('Erreur suppression', err);
        this.toast.error(err.error?.message || 'Erreur lors de l\'annulation du match.');
      }
    });
  }
}
