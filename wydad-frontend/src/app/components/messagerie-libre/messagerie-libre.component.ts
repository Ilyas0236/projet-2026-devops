import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

/**
 * V2.5 — Sélecteur libre d'interlocuteurs (1 ou N) avec message texte + photo.
 *
 * Réutilisé par les 4 espaces (joueur, staff, entraîneur, président) via le
 * bouton "✏️ Nouveau message" de l'onglet Comms. L'IDOR strict est délégué
 * à {@code sendMessage} (1 cible) ou {@code sendMessageBatch} (N cibles) :
 * le back refuse toute cible hors-discipline / hors-catégorie. Le front n'a
 * pas à connaître la règle métier — il envoie ce que l'utilisateur a coché.
 *
 * <p>Le parent fournit la liste des interlocuteurs (déjà filtrée par sa
 * propre logique d'annuaire discipline+catégorie) ; ce composant n'a PAS
 * d'appel réseau pour charger les contacts — il les reçoit en {@code @Input}.</p>
 */
@Component({
  selector: 'app-messagerie-libre',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './messagerie-libre.component.html',
  styleUrls: ['./messagerie-libre.component.css']
})
export class MessagerieLibreComponent implements OnInit {
  /**
   * Liste des interlocuteurs. Chaque contact doit exposer au minimum
   * `userId` et `fullName`. Champs optionnels : `role`, `subLabel`
   * (ex. "SENIOR · FOOTBALL"), `sportType`, `category`.
   */
  @Input({ required: true }) contacts: any[] = [];

  /** Pré-cocher un interlocuteur (mode "réponse directe depuis annuaire"). */
  @Input() prefillToUserId: number | null = null;

  /** Titre affiché en en-tête (ex. "Nouveau message — ma discipline"). */
  @Input() title = 'Nouveau message';

  /** Émis après envoi réussi. Le parent peut fermer le panneau. */
  @Output() sent = new EventEmitter<void>();
  /** Émis si l'utilisateur annule. */
  @Output() cancelled = new EventEmitter<void>();

  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  selectedIds = new Set<number>();
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

  readonly MAX_LEN = 500;

  ngOnInit() {
    this.contacts = (this.contacts || []).filter((c: any) =>
      c && c.userId && Number(c.userId) !== Number(this.auth.getCurrentUserId())
    );
    if (this.prefillToUserId) {
      this.selectedIds.add(Number(this.prefillToUserId));
    }
  }

  // ─────────── SÉLECTION ───────────

  toggle(userId: number) {
    if (this.selectedIds.has(userId)) this.selectedIds.delete(userId);
    else this.selectedIds.add(userId);
  }

  isSelected(userId: number): boolean {
    return this.selectedIds.has(userId);
  }

  selectAll() {
    this.contacts.forEach((c: any) => this.selectedIds.add(Number(c.userId)));
  }

  selectNone() {
    this.selectedIds.clear();
  }

  // ─────────── LABELS / VALIDATION ───────────

  recipientsLabel(): string {
    if (this.selectedIds.size === 0) return '— choisir au moins 1 interlocuteur —';
    if (this.selectedIds.size === 1) {
      const id = [...this.selectedIds][0];
      const c = this.contacts.find((x: any) => Number(x.userId) === id);
      return c ? this.displayName(c) : `#${id}`;
    }
    return `${this.selectedIds.size} personnes sélectionnées`;
  }

  canSend(): boolean {
    const hasContent = this.draft.trim().length > 0 || !!this.pendingAttachment;
    return hasContent
      && this.selectedIds.size > 0
      && this.draft.length <= this.MAX_LEN
      && !this.sending
      && !this.photoUploading;
  }

  // ─────────── PIÈCE JOINTE ───────────

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
      this.toast.error('Une photo est déjà attachée. Retire-la d\'abord.');
      input.value = '';
      return;
    }
    this.photoUploading = true;
    this.api.uploadMessageAttachment(file).subscribe({
      next: (res) => {
        this.pendingAttachment = res;
        this.photoUploading = false;
        this.toast.success('Photo prête.');
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

  // ─────────── ENVOI ───────────

  send() {
    if (!this.canSend()) return;
    this.sending = true;
    const targets = [...this.selectedIds];
    const text = this.draft.trim();
    const att = this.pendingAttachment ?? undefined;
    // 1 cible → /send (cohérence avec la messagerie 1-to-1 historique) ;
    // N cibles → /send-batch (fan-out applicatif back).
    const obs = targets.length === 1
      ? this.api.sendMessage(targets[0], text, att)
      : this.api.sendMessageBatch(targets, text, att);
    obs.subscribe({
      next: () => {
        this.sending = false;
        this.draft = '';
        this.pendingAttachment = null;
        this.selectedIds.clear();
        const n = targets.length;
        this.toast.success(n === 1
          ? 'Message envoyé.'
          : `Message envoyé à ${n} personnes.`);
        this.sent.emit();
      },
      error: (err) => {
        this.sending = false;
        const msg = err?.error?.message || err?.message || 'Échec envoi.';
        this.toast.error(msg);
      }
    });
  }

  cancel() {
    this.cancelled.emit();
  }

  // ─────────── HELPERS TEMPLATE ───────────

  displayName(c: any): string {
    return c?.fullName || c?.nom || c?.name || c?.email || `Membre #${c?.userId}`;
  }

  subLabel(c: any): string {
    if (c?.subLabel) return c.subLabel;
    const bits: string[] = [];
    if (c?.role && c.role !== 'JOUEUR') bits.push(this.roleLabel(c.role));
    if (c?.sportType) bits.push(c.sportType);
    if (c?.category) bits.push(c.category);
    return bits.join(' · ');
  }

  roleLabel(role: string): string {
    switch ((role || '').toUpperCase()) {
      case 'JOUEUR': return 'Joueur';
      case 'STAFF': return 'Staff';
      case 'ENTRAINEUR': return 'Entraîneur';
      case 'PRESIDENT': return 'Président';
      case 'ADMIN': return 'Admin';
      case 'JOURNALISTE': return 'Journaliste';
      case 'PARENT': return 'Parent';
      case 'ADHERENT': return 'Adhérent';
      default: return role;
    }
  }
}
