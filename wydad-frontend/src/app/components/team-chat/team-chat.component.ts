import { Component, EventEmitter, Input, OnInit, OnDestroy, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import {
  TeamChatService,
  TeamChatMessage,
} from '../../services/team-chat.service';

/**
 * Phase 4 — panneau de chat de GROUPE « WhatsApp » (texte uniquement, 500
 * caractères max). Réutilisé par l'espace joueur et l'espace staff : le groupe
 * est déterminé par la fiche du connecté (sport + catégorie), l'adhésion est
 * revérifiée côté serveur à chaque envoi.
 *
 * <p>Indicateur de connexion en temps réel (CONNECTING / EN LIGNE / HORS LIGNE),
 * historique persisté chargé au démarrage via le repli REST, diffusion STOMP.</p>
 */
@Component({
  selector: 'app-team-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './team-chat.component.html'
})
export class TeamChatComponent implements OnInit, OnDestroy {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);
  private chat = inject(TeamChatService);

  /** Groupe à ouvrir (sport/catégorie de la fiche du connecté). */
  @Input({ required: true }) sportType!: string;
  @Input({ required: true }) category!: string;

  /** Émis quand l'utilisateur replie le panneau. */
  @Output() closed = new EventEmitter<void>();

  messages: TeamChatMessage[] = [];
  members: any[] = [];
  draft = '';
  sending = false;
  loadingHistory = true;

  // V2.5 — pièce jointe en attente d'envoi (photo).
  pendingAttachment: { publicId: string; secureUrl?: string;
                       resourceType?: string; fileName?: string;
                       sizeBytes?: number; previewUrl?: string } | null = null;
  uploadingPhoto = false;

  // Indicateur de connexion (abonné dans ngOnInit)
  connectionState: 'CONNECTING' | 'OPEN' | 'CLOSED' = 'CLOSED';

  private myUserId = Number(this.auth.getCurrentUserId());
  private sub: any = null;

  readonly MAX_LEN = 500;

  get canSend(): boolean {
    const hasText = this.draft.trim().length > 0;
    const hasAttachment = !!this.pendingAttachment;
    return (hasText || hasAttachment)
      && this.draft.length <= this.MAX_LEN
      && this.connectionState === 'OPEN'
      && !this.sending
      && !this.uploadingPhoto;
  }

  ngOnInit() {
    if (!this.sportType || !this.category) {
      this.loadingHistory = false;
      return;
    }
    this.myUserId = Number(this.auth.getCurrentUserId());

    // Historique persisté (repli REST) puis diffusion temps réel.
    this.api.getTeamChatHistory(this.sportType, this.category).subscribe({
      next: list => {
        this.messages = Array.isArray(list) ? list : [];
        this.loadingHistory = false;
        this.scrollToBottom();
      },
      error: () => { this.loadingHistory = false; }
    });
    this.api.getTeamChatMembers(this.sportType, this.category).subscribe({
      next: m => this.members = Array.isArray(m) ? m : [],
      error: () => {}
    });

    this.chat.connectionState$.subscribe(state => {
      this.connectionState = state;
      if (state === 'OPEN') {
        this.chat.presence(this.sportType, this.category, true);
      }
    });

    this.sub = this.chat.connect(this.sportType, this.category)
      .subscribe(msg => this.onIncomingMessage(msg));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.connectionState === 'OPEN') {
      this.chat.presence(this.sportType, this.category, false);
      // Le service reste partagé entre les pages ; on ne coupe que si on est
      // le dernier utilisateur — simplification : on laisse la connexion vivre.
    }
  }

  onIncomingMessage(msg: TeamChatMessage) {
    const topicKey = `${msg.sportType.toLowerCase()}|${msg.category.toLowerCase()}`;
    const groupKey = `${this.sportType.toLowerCase()}|${this.category.toLowerCase()}`;
    if (topicKey !== groupKey) { return; } // message d'un autre groupe ouvert ailleurs
    this.messages.push(msg);
    this.scrollToBottom();
  }

  send() {
    if (!this.canSend) { return; }
    this.sending = true;
    const content = this.draft.trim();
    const att = this.pendingAttachment;
    if (att) {
      // Envoi avec photo via le repli REST directement (le WS ne porte pas
      // l'attachment en V2.5 — on garde le STOMP pour le texte only).
      this.api.sendTeamChatMessage(this.sportType, this.category, content, att).subscribe({
        next: () => {
          this.sending = false;
          this.draft = '';
          this.clearPendingAttachment();
          // On recharge l'historique (le message a été persisté) — la liste
          // se mettra à jour en WS dès que la diffusion parviendra.
          this.refreshHistory();
        },
        error: (err) => {
          this.sending = false;
          this.toast.error(err?.error?.message || 'Échec envoi de la photo.');
        }
      });
      return;
    }
    this.chat.send(this.sportType, this.category, content);
    // Le message revient par la diffusion serveur (source de vérité) ;
    // si rien ne revient sous ~3 s, on retente via le repli REST.
    setTimeout(() => {
      if (!this.messages.some(m =>
          m.content === content && Date.now() - new Date(m.createdAt).getTime() < 10000)) {
        this.api.sendTeamChatMessage(this.sportType, this.category, content).subscribe({
          next: () => {},
          error: () => this.toast.error('Message non envoyé. Vérifiez votre connexion.')
        });
        this.sending = false;
        this.draft = '';
      } else {
        this.sending = false;
        this.draft = '';
      }
    }, 1500);
  }

  /**
   * V2.5 — sélection d'une photo : on l'upload immédiatement (réutilise
   * l'endpoint /upload de la messagerie 1-to-1 qui prend en charge le mode
   * Cloudinary + mode local). On garde les métadonnées pour les passer
   * à {@code sendTeamChatMessage} à l'envoi du message.
   */
  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0];
    if (!file) { return; }
    if (!/^image\//.test(file.type)) {
      this.toast.error('Seules les images sont supportées dans le chat de groupe.');
      input.value = '';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.toast.error('Photo trop lourde (10 Mo max).');
      input.value = '';
      return;
    }
    this.uploadingPhoto = true;
    this.api.uploadMessageAttachment(file).subscribe({
      next: (res) => {
        this.uploadingPhoto = false;
        if (!res?.publicId) {
          this.toast.error('Échec de l\'upload.');
          return;
        }
        this.pendingAttachment = {
          publicId: res.publicId,
          secureUrl: res.secureUrl,
          resourceType: res.resourceType || 'image',
          fileName: res.fileName || file.name,
          sizeBytes: res.sizeBytes,
          // Aperçu local : si l'upload n'a pas renvoyé d'URL (mode local),
          // on garde l'object URL pour que l'UI montre la photo en preview.
          previewUrl: res.secureUrl
              || (res.publicId ? URL.createObjectURL(file) : undefined)
        };
        input.value = '';
      },
      error: (err) => {
        this.uploadingPhoto = false;
        this.toast.error(err?.error?.message || 'Échec de l\'upload de la photo.');
        input.value = '';
      }
    });
  }

  removePhoto(): void {
    this.clearPendingAttachment();
  }

  private clearPendingAttachment(): void {
    if (this.pendingAttachment?.previewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(this.pendingAttachment.previewUrl);
    }
    this.pendingAttachment = null;
  }

  private refreshHistory(): void {
    this.api.getTeamChatHistory(this.sportType, this.category).subscribe({
      next: list => {
        this.messages = Array.isArray(list) ? list : [];
        this.scrollToBottom();
      }
    });
  }

  isMine(msg: TeamChatMessage): boolean {
    return msg.senderUserId === this.myUserId;
  }

  /** V2.5 — vrai si le message porte une pièce jointe image. */
  isImageMsg(msg: TeamChatMessage): boolean {
    const t = (msg.mediaType || '').toUpperCase();
    return !!msg.mediaUrl && (t === 'IMAGE' || t === 'IMAGE/JPEG' || t === 'IMAGE/PNG');
  }

  /** V2.5 — URL d'affichage de la photo (signedUrl pour les anciens messages
   *  qui n'auraient pas l'URL directe en clair). */
  attachmentUrlFor(msg: TeamChatMessage): string | null {
    if (msg.mediaUrl) { return msg.mediaUrl; }
    // Pas de cache signé ici (pas de GET /attachment sur team-chat) — on
    // s'en remet à l'URL persistée par le serveur, qui est l'URL sécurisée
    // Cloudinary au moment de l'envoi.
    return null;
  }

  timeOf(iso: string): string {
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' :
      d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  /** Libellé court du rôle expéditeur dans l'en-tête de bulle. */
  roleLabel(role: string): string {
    switch (role) {
      case 'ENTRAINEUR': return 'Entraîneur';
      case 'STAFF': return 'Staff';
      case 'ADMIN': return 'Club';
      case 'PRESIDENT': return 'Président';
      case 'JOUEUR': return 'Joueur';
      default: return role;
    }
  }

  /** Entrée envoie, Maj+Entrée fait un saut de ligne. */
  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  /** Bouton « Hors ligne · Réessayer » : relance la connexion STOMP. */
  retryConnection() {
    this.chat.disconnect();
    this.sub?.unsubscribe();
    this.chat.connect(this.sportType, this.category)
      .subscribe(msg => this.onIncomingMessage(msg));
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = document.querySelector('.team-chat-messages');
      el?.scrollTo(0, el.scrollHeight);
    });
  }
}
