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

  // Indicateur de connexion (abonné dans ngOnInit)
  connectionState: 'CONNECTING' | 'OPEN' | 'CLOSED' = 'CLOSED';

  private myUserId = Number(this.auth.getCurrentUserId());
  private sub: any = null;

  readonly MAX_LEN = 500;

  get canSend(): boolean {
    return this.draft.trim().length > 0
      && this.draft.length <= this.MAX_LEN
      && this.connectionState === 'OPEN'
      && !this.sending;
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

  isMine(msg: TeamChatMessage): boolean {
    return msg.senderUserId === this.myUserId;
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
