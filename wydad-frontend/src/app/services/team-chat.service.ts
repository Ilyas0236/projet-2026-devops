import { Injectable, NgZone, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/** Message de groupe tel que sérialisé par TeamMessage (backend). */
export interface TeamChatMessage {
  id: number;
  sportType: string;
  category: string;
  senderUserId: number;
  senderName: string;
  senderRole: string;
  content: string;
  createdAt: string;
}

export interface GroupMember {
  userId: number;
  fullName: string;
  role: string;
}

/**
 * Phase 4 — client STOMP du chat de groupe « WhatsApp ».
 *
 * <p>Le JWT transite en en-tête natif du frame CONNECT (l'upgrade WebSocket
 * ne peut pas porter de header Authorization) ; communication-service valide
 * la signature lui-même. L'adhésion au groupe est revérifiée côté serveur à
 * chaque envoi.</p>
 */
@Injectable({ providedIn: 'root' })
export class TeamChatService {
  private auth = inject(AuthService);
  private zone = inject(NgZone);

  private client: Client | null = null;
  private connected = false;

  /** État de connexion exposé pour l'indicateur visuel du composant. */
  readonly connectionState$ = new BehaviorSubject<'CONNECTING' | 'OPEN' | 'CLOSED'>('CLOSED');

  /** Messages diffusés par le serveur sur /topic/chat/{sport}/{category}. */
  private messageEvents = new Subject<TeamChatMessage>();

  /** Groupes souscrits (pour re-souscrire après une reconnexion). */
  private groups = new Set<string>();
  private subscriptions: StompSubscription[] = [];

  /** WS endpoint : même origine que l'API (gateway → communication-service). */
  private wsUrl(): string {
    const base = environment.apiBaseUrl.replace(/\/api\/?$/, '');
    return `${base}/ws/team-chat`;
  }

  /**
   * Ouvre (ou réutilise) la connexion STOMP et s'abonne au groupe donné.
   * Idempotent : un seul client, plusieurs groupes possibles.
   */
  connect(sportType: string, category: string): Observable<TeamChatMessage> {
    if (!this.client) {
      const token = localStorage.getItem('wydad_token');
      this.client = new Client({
        webSocketFactory: () => new SockJS(this.wsUrl()),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        // Reconnexion automatique (réseau mobile des joueurs)
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          this.connected = true;
          this.zone.run(() => this.connectionState$.next('OPEN'));
          // Après reconnexion les anciennes souscriptions sont mortes.
          this.resubscribeAll();
        },
        onWebSocketClose: () => {
          this.connected = false;
          this.zone.run(() => this.connectionState$.next('CLOSED'));
        },
        onStompError: (frame) => {
          console.error('STOMP error', frame.headers['message']);
        }
      });
      this.client.activate();
    }

    const topic = this.topicFor(sportType, category);
    if (!this.groups.has(topic)) {
      this.groups.add(topic);
      this.subscribeToGroup(sportType, category);
    }
    return this.messageEvents.asObservable();
  }

  private topicFor(sportType: string, category: string): string {
    return `/topic/chat/${sportType.toLowerCase()}/${category.toLowerCase()}`;
  }

  private subscribeToGroup(sportType: string, category: string) {
    if (!this.client || !this.client.connected) { return; }
    const sub = this.client.subscribe(this.topicFor(sportType, category), (msg: IMessage) => {
      try {
        const parsed = JSON.parse(msg.body) as TeamChatMessage;
        // Diffusion dans la zone Angular pour déclencher le rendu.
        this.zone.run(() => this.messageEvents.next(parsed));
      } catch { /* frame non-JSON ignoré */ }
    });
    this.subscriptions.push(sub);
  }

  private resubscribeAll() {
    this.subscriptions = [];
    for (const topic of this.groups) {
      // topic = /topic/chat/{sport}/{category}
      const [, , , sport, category] = topic.split('/');
      this.subscribeToGroup(sport, category);
    }
  }

  /** Envoi d'un message via /app/chat/{sport}/{category}/send. */
  send(sportType: string, category: string, content: string): void {
    if (!this.client?.connected) { return; }
    this.client.publish({
      destination: `/app/chat/${sportType.toLowerCase()}/${category.toLowerCase()}/send`,
      body: JSON.stringify({ content })
    });
  }

  /** Signale arrivée/départ dans le groupe (pour les notifs hors ligne). */
  presence(sportType: string, category: string, online: boolean): void {
    if (!this.client?.connected) { return; }
    this.client.publish({
      destination: `/app/chat/${sportType.toLowerCase()}/${category.toLowerCase()}/presence`,
      body: JSON.stringify({ online })
    });
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.subscriptions = [];
      this.connected = false;
      this.connectionState$.next('CLOSED');
    }
  }

  isConnected(): boolean {
    return this.connected;
  }
}
