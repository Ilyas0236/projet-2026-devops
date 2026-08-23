import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';

/**
 * Espace joueur connecté (B.3) : profil restreint, convocations (B.3.a),
 * historique de présence, documents partagés par le staff.
 * Toutes les données proviennent du backend filtrées par l'identité JWT ;
 * le serveur garantit l'ownership (403 prouvé côté tests).
 */
@Component({
  selector: 'app-dashboard-joueur',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ErrorBannerComponent],
  templateUrl: './dashboard-joueur.component.html'
})
export class DashboardJoueurComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  player: any = null;
  sessions: any[] = [];
  loading = true;
  loadError = false;

  // Convocations / présence / documents / stats détaillées
  convocations: any[] = [];
  presence: any[] = [];
  documents: any[] = [];
  matchStats: any[] = [];

  // Réponse en cours à une convocation (ABSENT/RETARD → justification)
  respondingId: number | null = null;
  respondingJustification = '';
  respondingStatus: 'ABSENT' | 'RETARD' | null = null;
  submittingResponse = false;

  // Édition de profil restreinte (jamais numéro/poste/catégorie : whitelist serveur)
  editProfileOpen = false;
  savingProfile = false;
  editHeight: number | null = null;
  editWeight: number | null = null;
  editBirthDate = '';
  editNationality = '';

  ngOnInit() {
    const userId = this.auth.getCurrentUserId();
    if (!userId) {
      this.loading = false;
      return;
    }
    this.api.getPlayerByUserId(userId).subscribe({
      next: (data) => {
        this.player = data;
        this.loadSessions();
        this.loadMySpace();
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  loadSessions() {
    if (this.player && this.player.sportType && this.player.category) {
      this.api.getSessionsByCategory(this.player.sportType, this.player.category).subscribe({
        next: (data) => {
          this.sessions = data;
          this.loading = false;
        },
        error: () => { this.loading = false; }
      });
    } else {
      this.loading = false;
    }
  }

  /** Convocations + historique + documents de MON espace (filtrés par le backend). */
  loadMySpace() {
    this.api.getMyConvocations().subscribe({ next: d => this.convocations = d, error: () => {} });
    this.api.getMyPresence().subscribe({ next: d => this.presence = d, error: () => {} });
    this.api.getMyDocuments().subscribe({ next: d => this.documents = d, error: () => {} });
    this.api.getMyStats().subscribe({ next: d => this.matchStats = d, error: () => {} });
  }

  retryLoad() {
    this.loadError = false;
    this.loading = true;
    this.ngOnInit();
  }

  // ───────────────────── Réponse à une convocation ─────────────────────

  confirm(c: any) {
    this.submittingResponse = true;
    this.api.respondToConvocation(c.id, 'CONFIRME').subscribe({
      next: () => {
        this.toast.success('Présence confirmée');
        this.submittingResponse = false;
        this.reloadConvocations();
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Échec de la confirmation');
        this.submittingResponse = false;
      }
    });
  }

  openJustification(c: any, status: 'ABSENT' | 'RETARD') {
    this.respondingId = c.id;
    this.respondingStatus = status;
    this.respondingJustification = c.responseJustification || '';
  }

  cancelJustification() {
    this.respondingId = null;
    this.respondingStatus = null;
    this.respondingJustification = '';
  }

  submitJustification() {
    if (!this.respondingJustification.trim()) { return; }
    this.submittingResponse = true;
    this.api.respondToConvocation(this.respondingId!, this.respondingStatus!, this.respondingJustification.trim())
      .subscribe({
        next: () => {
          this.toast.success('Réponse enregistrée');
          this.cancelJustification();
          this.submittingResponse = false;
          this.reloadConvocations();
        },
        error: (err) => {
          this.toast.error(err?.error?.message || 'Échec de l\'envoi');
          this.submittingResponse = false;
        }
      });
  }

  private reloadConvocations() {
    this.api.getMyConvocations().subscribe({ next: d => this.convocations = d });
    this.api.getMyPresence().subscribe({ next: d => this.presence = d });
  }

  // ───────────────────── Édition de profil restreinte ─────────────────────

  openProfileEdit() {
    this.editHeight = this.player.height ?? null;
    this.editWeight = this.player.weight ?? null;
    this.editBirthDate = this.player.birthDate ? String(this.player.birthDate).substring(0, 10) : '';
    this.editNationality = this.player.nationality || '';
    this.editProfileOpen = true;
  }

  saveProfile() {
    this.savingProfile = true;
    this.api.updateMyProfile({
      height: this.editHeight,
      weight: this.editWeight,
      birthDate: this.editBirthDate || null,
      nationality: this.editNationality || null
    }).subscribe({
      next: (updated) => {
        this.player = updated;
        this.editProfileOpen = false;
        this.savingProfile = false;
        this.toast.success('Profil mis à jour');
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Échec de la mise à jour');
        this.savingProfile = false;
      }
    });
  }
}
