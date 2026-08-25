import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';
import { TeamChatComponent } from '../../../components/team-chat/team-chat.component';
import { MyCallsComponent } from '../../../components/my-calls/my-calls.component';

/**
 * Espace joueur connecté (B.3) : profil restreint, convocations (B.3.a),
 * historique de présence, documents partagés par le staff.
 * Toutes les données proviennent du backend filtrées par l'identité JWT ;
 * le serveur garantit l'ownership (403 prouvé côté tests).
 */
@Component({
  selector: 'app-dashboard-joueur',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ErrorBannerComponent, TeamChatComponent, MyCallsComponent],
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

  // Messagerie (B.5) : écrire au staff de MA catégorie (contrôle serveur)
  inbox: any[] = [];
  conversation: any[] = [];
  conversationWith: { id: number; name: string } | null = null;
  messageDraft = '';
  sendingMessage = false;

  // Annonces visibles : club + ma catégorie (filtrage serveur)
  announcements: any[] = [];

  // B.11 : espace de notifications UNIQUE (convocations, médical, messages,
  // réponses du club…) — ownership prouvé côté serveur (assertSelfOrAdmin).
  notifications: any[] = [];
  unreadNotifications = 0;
  markingNotificationId: number | null = null;

  // Transparence financière — rapports publiés par le club
  rapportsFinanciers: any[] = [];

  loadRapportsFinanciers() {
    this.api.getRapportsFinanciers().subscribe({
      next: (list) => (this.rapportsFinanciers = Array.isArray(list) ? list.slice(0, 3) : []),
      error: () => (this.rapportsFinanciers = [])
    });
  }

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
        this.loadRapportsFinanciers();
        this.loadVipTickets(userId);
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  // Phase 2 — billets VIP du joueur (4 par match à domicile, générés auto)
  vipTickets: any[] = [];
  vipTicketsLoading = false;
  vipDownloadingId: number | null = null;

  /** Billets VIP uniquement (catégorie VIP), les plus proches en premier. */
  loadVipTickets(userId: number) {
    this.vipTicketsLoading = true;
    this.api.getTicketsByUser(userId).subscribe({
      next: (data) => {
        this.vipTickets = (data || [])
          .filter((t: any) => t.category === 'VIP')
          .sort((a: any, b: any) =>
            new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime());
        this.vipTicketsLoading = false;
      },
      error: () => { this.vipTicketsLoading = false; }
    });
  }

  downloadVipPdf(ticket: any) {
    this.vipDownloadingId = ticket.id;
    this.api.getTicketPdf(ticket.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `billet-vip-${ticket.ticketNumber}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.vipDownloadingId = null;
      },
      error: () => {
        this.toast.error('Erreur lors du téléchargement du billet VIP.');
        this.vipDownloadingId = null;
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
    this.api.getMyConvocations().subscribe({
      next: d => {
        // Garde-fou : une payload non-tableau (réponse dégradée) ne casse pas l'espace.
        const list = Array.isArray(d) ? d : [];
        this.convocations = list;
        // Phase 3 — accusé de lecture : consulter sa boîte = lire ses
        // convocations. Idempotent côté serveur ; alimente le suivi entraîneur.
        list.filter(c => !c.readAt).forEach(c => this.markConvocationRead(c));
      },
      error: () => {}
    });
    this.api.getMyPresence().subscribe({ next: d => this.presence = d, error: () => {} });
    this.api.getMyDocuments().subscribe({ next: d => this.documents = d, error: () => {} });
    this.api.getMyStats().subscribe({ next: d => this.matchStats = d, error: () => {} });
    this.api.getInbox().subscribe({ next: d => this.inbox = d, error: () => {} });
    this.api.getVisibleAnnouncements().subscribe({ next: d => this.announcements = d, error: () => {} });
    this.loadNotifications();
  }

  /** B.11 : charge l'inbox de notifications du membre connecté. */
  loadNotifications() {
    const userId = this.auth.getCurrentUserId();
    if (!userId) { return; }
    this.api.getMyNotifications(userId).subscribe({
      next: d => this.notifications = d || [],
      error: () => {}
    });
    this.api.getMyUnreadCount(userId).subscribe({
      next: n => this.unreadNotifications = n,
      error: () => {}
    });
  }

  /** Marque une notification comme lue (endpoint assertSelfOrAdmin). */
  markNotificationRead(n: any) {
    if (n.status === 'READ' || this.markingNotificationId) { return; }
    this.markingNotificationId = n.id;
    this.api.markNotificationRead(n.id).subscribe({
      next: () => {
        n.status = 'READ';
        this.unreadNotifications = Math.max(0, this.unreadNotifications - 1);
        this.markingNotificationId = null;
      },
      error: () => { this.markingNotificationId = null; }
    });
  }

  /** Libellé court selon le type serveur (IN_APP, EMAIL…). */
  notificationTypeLabel(type: string): string {
    switch (type) {
      case 'IN_APP': return 'Club';
      case 'EMAIL': return 'E-mail';
      case 'PUSH': return 'Push';
      default: return type;
    }
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

  /**
   * Phase 3 — accusé de lecture silencieux (fire-and-forget) : le joueur
   * a vu sa convocation, l'entraîneur le voit dans son suivi. Idempotent.
   */
  markConvocationRead(c: any) {
    this.api.markConvocationRead(c.id).subscribe({
      next: () => { c.readAt = new Date().toISOString(); },
      error: () => {}
    });
  }

  /** Pastille de type média (même code couleur que la sidebar staff). */
  mediaTypeLabel(mediaType: string | null | undefined): string {
    switch (mediaType) {
      case 'VIDEO': return 'Vidéo';
      case 'PHOTO': return 'Photo';
      default: return 'Document';
    }
  }

  private reloadConvocations() {
    this.api.getMyConvocations().subscribe({ next: d => this.convocations = d });
    this.api.getMyPresence().subscribe({ next: d => this.presence = d });
  }

  // ───────────────────── Messagerie (B.5) ─────────────────────

  openConversation(staffUserId: number, staffName: string) {
    this.conversationWith = { id: staffUserId, name: staffName };
    this.api.getConversation(staffUserId).subscribe({
      next: d => this.conversation = d,
      error: () => this.toast.error('Impossible de charger la conversation')
    });
  }

  closeConversation() {
    this.conversationWith = null;
    this.conversation = [];
    this.messageDraft = '';
  }

  sendMessageToStaff() {
    if (!this.messageDraft.trim() || !this.conversationWith) { return; }
    this.sendingMessage = true;
    const myId = Number(this.auth.getCurrentUserId());
    this.api.sendMessage(this.conversationWith.id, this.messageDraft.trim()).subscribe({
      next: () => {
        // Optimiste local : le serveur a validé l'appariement avant de persister
        this.conversation = [...this.conversation, {
          senderUserId: myId,
          recipientUserId: this.conversationWith!.id,
          content: this.messageDraft.trim(),
          createdAt: new Date().toISOString()
        }];
        this.messageDraft = '';
        this.sendingMessage = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Envoi impossible');
        this.sendingMessage = false;
      }
    });
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
