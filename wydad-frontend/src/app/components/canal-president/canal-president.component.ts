import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * V2.5 — Canal président : 1-to-1 persistant avec le président de la
 * discipline du connecté. Le back résout l'userId via
 * {@code GET /api/sports/messaging/my-president} ; ce composant ne fait
 * que réutiliser l'UI messagerie 1-to-1 existante (inbox, fil, photo).
 *
 * <p>Si l'utilisateur n'a pas de fiche sportive (ADMIN, JOURNALISTE…)
 * ou si aucun président n'est enregistré pour sa discipline, le panneau
 * affiche un message pédagogique — pas d'écran vide muet.</p>
 */
@Component({
  selector: 'app-canal-president',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './canal-president.component.html',
  styleUrls: ['./canal-president.component.css']
})
export class CanalPresidentComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  loading = true;
  president: { userId: number; email: string; discipline: string; role: string } | null = null;
  conversation: any[] = [];
  draft = '';
  sending = false;
  photoUploading = false;
  pendingAttachment: {
    publicId: string;
    secureUrl: string;
    resourceType: string;
    fileName: string;
    sizeBytes: number;
  } | null = null;
  /** Cache signedUrl → on évite de re-frapper /attachment à chaque rendu. */
  private signedUrlCache = new Map<number, string>();

  private myUserId = Number(this.auth.getCurrentUserId());
  readonly MAX_LEN = 500;

  ngOnInit() {
    this.api.getMyPresident().subscribe({
      next: (p) => {
        this.president = p || null;
        this.loading = false;
        if (this.president) this.loadConversation();
      },
      error: () => {
        this.loading = false;
        this.toast.error('Impossible de joindre la messagerie du président.');
      }
    });
  }

  loadConversation() {
    if (!this.president) return;
    this.api.getConversation(this.president.userId).subscribe({
      next: (msgs) => {
        this.conversation = Array.isArray(msgs) ? msgs : [];
        this.resolveAttachments();
        this.scrollToBottom();
      },
      error: () => {
        this.toast.error('Impossible de charger la conversation.');
      }
    });
  }

  /**
   * V2.5 — pour les anciens messages on n'a pas toujours {@code secureUrl}
   * (mode local ou message persisté avant Cloudinary). On demande une URL
   * signée fraîche à l'API et on la cache pour les rendus suivants.
   */
  private resolveAttachments() {
    for (const m of this.conversation) {
      if (m.attachmentPublicId && !m.attachmentSecureUrl
          && !this.signedUrlCache.has(m.id)) {
        this.api.getMessageAttachmentUrl(m.id).subscribe({
          next: (r) => {
            if (r?.url) this.signedUrlCache.set(m.id, r.url);
          },
          error: () => { /* ignore — l'UI affichera le placeholder */ }
        });
      }
    }
  }

  attachmentUrlFor(m: any): string | null {
    if (m.attachmentSecureUrl) return m.attachmentSecureUrl;
    return this.signedUrlCache.get(m.id) || null;
  }

  isImageMsg(m: any): boolean {
    const t = (m.attachmentResourceType || '').toUpperCase();
    return !!this.attachmentUrlFor(m)
        && (t === 'IMAGE' || t === 'IMAGE/JPEG' || t === 'IMAGE/PNG');
  }

  isMine(m: any): boolean {
    return m.senderUserId === this.myUserId;
  }

  timeOf(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' :
      d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  canSend(): boolean {
    return (this.draft.trim().length > 0 || !!this.pendingAttachment)
        && this.draft.length <= this.MAX_LEN
        && !!this.president
        && !this.sending
        && !this.photoUploading;
  }

  onPhotoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!/^image\//.test(file.type)) {
      this.toast.error('Seules les images sont supportées.');
      input.value = '';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.toast.error('Photo trop volumineuse (10 Mo max).');
      input.value = '';
      return;
    }
    if (this.pendingAttachment) {
      this.toast.error('Une photo est déjà attachée.');
      input.value = '';
      return;
    }
    this.photoUploading = true;
    this.api.uploadMessageAttachment(file).subscribe({
      next: (res) => {
        this.pendingAttachment = res;
        this.photoUploading = false;
      },
      error: (err) => {
        this.photoUploading = false;
        this.toast.error(err?.error?.message || 'Échec upload photo.');
      }
    });
    input.value = '';
  }

  removePhoto() {
    this.pendingAttachment = null;
  }

  send() {
    if (!this.canSend() || !this.president) return;
    this.sending = true;
    this.api.sendMessage(this.president.userId, this.draft.trim(),
                         this.pendingAttachment ?? undefined).subscribe({
      next: () => {
        this.sending = false;
        this.draft = '';
        this.pendingAttachment = null;
        this.loadConversation();
        this.toast.success('Message envoyé au président.');
      },
      error: (err) => {
        this.sending = false;
        this.toast.error(err?.error?.message || 'Échec envoi.');
      }
    });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = document.querySelector('.canal-president-messages');
      el?.scrollTo(0, el.scrollHeight);
    });
  }
}
