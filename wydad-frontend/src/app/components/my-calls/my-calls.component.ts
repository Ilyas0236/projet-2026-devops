import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * Phase 5 — « Mes appels » : agenda des appels vidéo/vocaux où je suis
 * organisateur ou participant, avec bouton « Rejoindre » qui obtient un
 * jeton LiveKit côté serveur puis ouvre la room via le SDK livekit-client.
 *
 * Utilisable dans les espaces joueur, staff et président (aucune logique
 * d'autorisation ici : le serveur filtre l'agenda et le jeton).
 */
@Component({
  selector: 'app-my-calls',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="paper-card rounded-3xl p-8">
      <div class="flex justify-between items-end mb-6">
        <div>
          <h3 class="section-title-light font-display text-2xl uppercase tracking-wider text-ink-primary">Appels vidéo</h3>
          <p class="text-ink-tertiary text-xs uppercase tracking-widest mt-1">Programmés par le club</p>
        </div>
        <span class="bg-paper-2 text-ink-secondary border border-paper-3 px-4 py-2 rounded-xl text-xs font-bold uppercase tracking-wider">
          {{ calls().length }} appel(s)
        </span>
      </div>

      <!-- Média non configuré côté serveur : avertissement discret -->
      <div *ngIf="mediaConfigured === false" class="mb-4 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
        Le service d'appels vidéo est momentanément indisponible — vous pouvez consulter le planning mais pas rejoindre.
      </div>

      <!-- Empty state -->
      <div *ngIf="!loading && calls().length === 0" class="flex flex-col items-center py-10 text-center">
        <div class="w-14 h-14 rounded-full bg-paper-2 border border-paper-3 flex items-center justify-center mb-3">
          <svg class="w-7 h-7 text-ink-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                  d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"/>
          </svg>
        </div>
        <p class="text-ink-secondary font-display uppercase tracking-wider">Aucun appel programmé</p>
        <p class="text-ink-tertiary text-xs mt-1">Les appels du club apparaîtront ici.</p>
      </div>

      <!-- Liste des appels -->
      <div class="space-y-4">
        <div *ngFor="let call of calls()"
             class="flex flex-col md:flex-row md:items-center gap-4 p-5 bg-paper-1 border border-paper-3 rounded-2xl hover:border-wydad-red/40 transition-all">
          <!-- Date/heure -->
          <div class="flex flex-row md:flex-col items-center md:items-start justify-center md:justify-start min-w-[100px] gap-1 shrink-0">
            <span class="text-wydad-red font-bold text-xs uppercase tracking-widest">{{ call.scheduledAt | date:'EEE d MMM' }}</span>
            <span class="text-2xl font-display font-black text-ink-primary">{{ call.scheduledAt | date:'HH:mm' }}</span>
            <span *ngIf="!call.scheduledAt" class="text-xs text-ink-tertiary uppercase">Immédiat</span>
          </div>

          <!-- Infos -->
          <div class="flex-1 min-w-0">
            <div class="flex flex-wrap items-center gap-2 mb-1">
              <h4 class="font-display font-bold uppercase tracking-wider text-ink-primary">{{ call.title }}</h4>
              <span *ngIf="call.organizerUserId === me"
                    class="px-2.5 py-0.5 bg-wydad-red text-white text-[9px] font-bold uppercase tracking-widest rounded-full">Organisateur</span>
              <span class="px-2.5 py-0.5 bg-paper-2 border border-paper-3 text-[9px] font-bold uppercase tracking-widest rounded-full {{ statusClass(call.status) }}">
                {{ statusLabel(call.status) }}
              </span>
            </div>
            <p class="text-ink-secondary text-xs">
              Par {{ call.organizerName }}
              <span *ngIf="call.durationMinutes"> · {{ call.durationMinutes }} min</span>
            </p>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-2 shrink-0">
            <button *ngIf="call.organizerUserId === me && call.status === 'PROGRAMME'"
                    (click)="cancel(call)"
                    class="px-4 py-2 rounded-xl border border-paper-3 bg-white text-ink-secondary text-xs font-bold uppercase tracking-wider hover:border-wydad-red/50 hover:text-wydad-red transition-all">
              Annuler
            </button>
            <button *ngIf="call.status !== 'ANNULE' && call.status !== 'TERMINE'"
                    (click)="join(call)"
                    [disabled]="joiningId === call.id"
                    class="px-5 py-2 rounded-xl bg-wydad-red text-white text-xs font-bold uppercase tracking-wider hover:bg-wydad-red/90 transition-all disabled:opacity-50">
              {{ joiningId === call.id ? 'Connexion…' : 'Rejoindre' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Overlay d'appel plein écran -->
      <div *ngIf="activeCall" class="fixed inset-0 z-[100] flex flex-col" style="background: rgba(10,10,12,.96)">
        <div class="flex items-center justify-between p-4 md:p-6">
          <div>
            <h4 class="text-white font-display font-bold uppercase tracking-wider">{{ activeCall.title }}</h4>
            <p class="text-white/60 text-xs uppercase tracking-widest mt-1">{{ participants().length }} participant(s)</p>
          </div>
          <button (click)="leave()"
                  class="px-4 py-2 rounded-xl bg-white/10 border border-white/20 text-white text-xs font-bold uppercase tracking-wider hover:bg-white/20 transition-all">
            Quitter
          </button>
        </div>

        <!-- Grille vidéo -->
        <div class="flex-1 grid gap-3 p-4 pt-0 md:p-6 md:pt-0 md:grid-cols-2 auto-rows-fr overflow-auto">
          <div *ngFor="let p of participants()"
               class="relative bg-black/40 border border-white/10 rounded-2xl overflow-hidden min-h-[180px]">
            <video *ngIf="p.videoStream; else audioOnly" [srcObject]="p.videoStream" autoplay playsinline
                   [muted]="p.isLocal" class="w-full h-full object-cover"></video>
            <ng-template #audioOnly>
              <div class="w-full h-full flex items-center justify-center">
                <div class="w-16 h-16 rounded-full bg-white/10 flex items-center justify-center">
                  <svg class="w-7 h-7 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                          d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                  </svg>
                </div>
              </div>
            </ng-template>
            <div class="absolute bottom-2 left-2 px-3 py-1 rounded-full bg-black/60 text-white text-[10px] font-bold uppercase tracking-widest">
              {{ p.name }}{{ p.isLocal ? ' (moi)' : '' }}
            </div>
          </div>
          <div *ngIf="participants().length === 0" class="md:col-span-2 flex flex-col items-center justify-center text-center py-16">
            <div class="w-16 h-16 rounded-full bg-white/10 flex items-center justify-center mb-3 animate-pulse">
              <svg class="w-8 h-8 text-white/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                      d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"/>
              </svg>
            </div>
            <p class="text-white/70 text-sm uppercase tracking-widest">En attente des participants…</p>
          </div>
        </div>

        <!-- Barre d'actions média -->
        <div class="flex items-center justify-center gap-3 p-4 md:p-6">
          <button (click)="toggleMic()" title="Micro"
                  class="w-14 h-14 rounded-full flex items-center justify-center border transition-all"
                  [class]="micOn ? 'bg-white/10 border-white/25 text-white' : 'bg-wydad-red text-white border-wydad-red'">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    [attr.d]="micOn
                      ? 'M19 11a7 7 0 01-14 0m7 7v4m-4 0h8M12 3a3 3 0 013 3v6a3 3 0 01-6 0V6a3 3 0 013-3z'
                      : 'M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.878 9.878L3 3m6.878 6.878L21 21'"/>
            </svg>
          </button>
          <button (click)="toggleCam()" title="Caméra"
                  class="w-14 h-14 rounded-full flex items-center justify-center border transition-all"
                  [class]="camOn ? 'bg-white/10 border-white/25 text-white' : 'bg-wydad-red text-white border-wydad-red'">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    [attr.d]="camOn
                      ? 'M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z'
                      : 'M3 3l18 18M15 10.5V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2h8a2 2 0 002-2v-.5L21 17V7l-6 3.5z'"/>
            </svg>
          </button>
          <button (click)="leave()" title="Raccrocher"
                  class="w-14 h-14 rounded-full bg-wydad-red text-white flex items-center justify-center hover:bg-wydad-red/90 transition-all rotate-[135deg]">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  `,
})
export class MyCallsComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  calls = signal<any[]>([]);
  loading = true;
  mediaConfigured: boolean | null = null;
  me = 0;

  joiningId: number | null = null;
  activeCall: any = null;

  participants = signal<{ identity: string; name: string; isLocal: boolean; videoStream?: MediaStream }[]>([]);
  micOn = true;
  camOn = true;

  private room: any = null;

  async ngOnInit() {
    this.me = Number(this.auth.decodeToken()?.sub) || 0;
    this.api.getMediaStatus().subscribe({
      next: (s) => (this.mediaConfigured = s.configured),
      error: () => (this.mediaConfigured = null),
    });
    this.load();
  }

  load() {
    this.loading = true;
    this.api.getMyCalls().subscribe({
      next: (calls) => { this.calls.set(calls ?? []); this.loading = false; },
      error: () => { this.loading = false; this.toast.error('Impossible de charger vos appels'); },
    });
  }

  statusLabel(s: string): string {
    const labels: Record<string, string> = {
      PROGRAMME: 'Programmé', EN_COURS: 'En cours', TERMINE: 'Terminé', ANNULE: 'Annulé',
    };
    return labels[s] ?? s;
  }

  statusClass(s: string): string {
    const classes: Record<string, string> = {
      PROGRAMME: 'text-ink-secondary',
      EN_COURS: 'text-wydad-red border-wydad-red/40',
      TERMINE: 'text-ink-tertiary',
      ANNULE: 'text-ink-tertiary line-through',
    };
    return classes[s] ?? '';
  }

  async join(call: any) {
    this.joiningId = call.id;
    try {
      const t = await firstValueFrom(this.api.getCallToken(call.id));
      // SDK chargé à la volée : ne pèse sur le bundle que quand on rejoint.
      const { Room, RoomEvent } = await import('livekit-client');
      const room = new Room({ adaptiveStream: true });
      this.room = room;

      const refresh = () => this.refreshParticipants(room);
      room.on(RoomEvent.TrackSubscribed, refresh);
      room.on(RoomEvent.TrackUnsubscribed, refresh);
      room.on(RoomEvent.ParticipantConnected, refresh);
      room.on(RoomEvent.ParticipantDisconnected, refresh);
      room.on(RoomEvent.LocalTrackPublished, refresh);
      room.on(RoomEvent.LocalTrackUnpublished, refresh);
      this.refreshParticipants(room);

      await room.connect(t.url, t.token);
      await room.localParticipant.setCameraEnabled(true);
      await room.localParticipant.setMicrophoneEnabled(true);
      this.micOn = true;
      this.camOn = true;
      this.activeCall = { ...call, roomName: t.roomName };
    } catch (e: any) {
      const srvMsg = e?.error?.message as string | undefined;
      this.toast.error(srvMsg || e?.message || "Connexion à l'appel impossible");
      this.cleanupRoom();
    } finally {
      this.joiningId = null;
    }
  }

  /** Reconstruit la grille vidéo : local + distants avec caméra active. */
  private refreshParticipants(room: any) {
    const list: { identity: string; name: string; isLocal: boolean; videoStream?: MediaStream }[] = [];

    const localCam = room.localParticipant.getTrackPublication('camera');
    list.push({
      identity: String(this.me),
      name: 'Moi',
      isLocal: true,
      videoStream: localCam?.videoTrack?.mediaStreamTrack
        ? new MediaStream([localCam.videoTrack.mediaStreamTrack])
        : undefined,
    });

    for (const p of (room.remoteParticipants?.values() ?? [])) {
      const cam = p.getTrackPublication('camera');
      list.push({
        identity: p.identity,
        name: p.name || 'Participant',
        isLocal: false,
        videoStream: cam?.isSubscribed && cam.videoTrack?.mediaStreamTrack
          ? new MediaStream([cam.videoTrack.mediaStreamTrack])
          : undefined,
      });
      // L'audio distant (microphone) est joué automatiquement par le SDK.
    }
    this.participants.set(list);
  }

  toggleMic() {
    if (!this.room) return;
    this.micOn = !this.micOn;
    void this.room.localParticipant.setMicrophoneEnabled(this.micOn);
  }

  toggleCam() {
    if (!this.room) return;
    this.camOn = !this.camOn;
    void this.room.localParticipant.setCameraEnabled(this.camOn);
  }

  leave() {
    this.cleanupRoom();
    this.load(); // rafraîchit les statuts après l'appel
  }

  private cleanupRoom() {
    try { this.room?.disconnect(); } catch { /* déjà parti */ }
    this.room = null;
    this.participants.set([]);
    this.activeCall = null;
  }

  cancel(call: any) {
    this.api.cancelCall(call.id).subscribe({
      next: () => { this.toast.success('Appel annulé'); this.load(); },
      error: (e) => this.toast.error(e?.error?.message || 'Annulation impossible'),
    });
  }
}
