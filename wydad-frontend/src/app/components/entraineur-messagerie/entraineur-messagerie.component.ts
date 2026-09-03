import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

/**
 * V2.4 — Messagerie entraîneur → joueurs (mode groupe OU individuel).
 *
 * <p>Remplace {@code <app-team-chat>} dans l'onglet Messagerie de l'espace
 * entraîneur. Le team-chat WhatsApp-like reste utilisé côté joueur et côté
 * staff, mais ne répond pas au besoin de l'entraîneur qui veut cibler
 * précisément ses joueurs.</p>
 *
 * <p>2 sous-onglets :
 * <ul>
 *   <li><b>👥 Mon équipe</b> : 1 clic → envoi à toute la catégorie (filtrée
 *       par sport+catég de la fiche staff) ;</li>
 *   <li><b>🎯 Individuel</b> : checkboxes 1 ou N joueurs cochés.</li>
 * </ul>
 * </p>
 *
 * <p>Pas de migration BDD : on délègue à {@code POST /send-batch} qui
 * fait le fan-out applicatif (1 message persisté par destinataire, IDOR
 * strict vérifié pour chacun via sameGroup mine/theirs).</p>
 */
@Component({
  selector: 'app-entraineur-messagerie',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './entraineur-messagerie.component.html',
  styleUrls: ['./entraineur-messagerie.component.css']
})
export class EntraineurMessagerieComponent implements OnInit {
  /** Fiche staff de l'entraîneur (sport+catég). Injectée par le dashboard parent. */
  @Input({ required: true }) staff: any;

  /** Liste des joueurs de la catégorie (déjà filtrée par le parent via getPlayersByCategory). */
  @Input({ required: true }) joueurs: any[] = [];

  api = inject(ApiService);
  toast = inject(ToastService);

  subTab: 'equipe' | 'individuel' = 'equipe';
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

  ngOnInit() {
    // Sécurité : si parent injecte des joueurs sans userId, on déduplique
    // et on filtre pour éviter les null dans les checkboxes.
    this.joueurs = (this.joueurs || []).filter((j: any) => j && j.userId);
  }

  // ─────────── SÉLECTION ───────────

  toggleJoueur(userId: number) {
    if (this.selectedIds.has(userId)) this.selectedIds.delete(userId);
    else this.selectedIds.add(userId);
  }

  isSelected(userId: number): boolean {
    return this.selectedIds.has(userId);
  }

  selectAll() {
    this.joueurs.forEach((j: any) => this.selectedIds.add(j.userId));
  }

  selectNone() {
    this.selectedIds.clear();
  }

  // ─────────── LABELS / VALIDATION ───────────

  recipientsLabel(): string {
    if (this.subTab === 'equipe') {
      return this.joueurs.length > 0
        ? `toute l'équipe (${this.joueurs.length})`
        : '— aucun joueur dans la catégorie —';
    }
    if (this.selectedIds.size === 0) return '— aucun —';
    if (this.selectedIds.size === 1) {
      const id = [...this.selectedIds][0];
      const j = this.joueurs.find((x: any) => x.userId === id);
      return j ? (j.fullName || j.nom || `Joueur #${id}`) : `#${id}`;
    }
    return `${this.selectedIds.size} joueurs sélectionnés`;
  }

  canSend(): boolean {
    const hasContent = this.draft.trim().length > 0 || !!this.pendingAttachment;
    if (!hasContent) return false;
    if (this.subTab === 'equipe') return this.joueurs.length > 0;
    return this.selectedIds.size > 0;
  }

  // ─────────── PIÈCE JOINTE ───────────

  onPhotoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
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
    // reset input pour pouvoir re-sélectionner le même fichier
    input.value = '';
  }

  removePhoto() {
    this.pendingAttachment = null;
  }

  // ─────────── ENVOI ───────────

  send() {
    if (!this.canSend() || this.sending) return;
    this.sending = true;
    const targets: number[] = this.subTab === 'equipe'
      ? this.joueurs.map((j: any) => j.userId)
      : [...this.selectedIds];
    const targetsSorted = Array.from(new Set(targets));

    this.api
      .sendMessageBatch(targetsSorted, this.draft.trim(), this.pendingAttachment ?? undefined)
      .subscribe({
        next: (msgs) => {
          this.sending = false;
          this.draft = '';
          this.pendingAttachment = null;
          this.selectedIds.clear();
          const n = Array.isArray(msgs) ? msgs.length : targetsSorted.length;
          this.toast.success(`Message envoyé à ${n} joueur(s).`);
        },
        error: (err) => {
          this.sending = false;
          const msg = err?.error?.message || err?.message || 'Échec envoi.';
          this.toast.error(msg);
        }
      });
  }

  // ─────────── HELPERS TEMPLATE ───────────

  displayName(j: any): string {
    return j?.fullName || j?.nom || j?.name || `Joueur #${j?.userId}`;
  }

  jerseyNumber(j: any): string {
    const n = j?.shirtNumber ?? j?.numeroMaillot;
    return n != null ? `#${n}` : '';
  }
}
