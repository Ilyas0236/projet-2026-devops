import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';
import { MyCallsComponent } from '../../../components/my-calls/my-calls.component';
import { ScheduleCallFormComponent } from '../../../components/my-calls/schedule-call-form.component';

/**
 * Espace Président (§11-§15) — interface dédiée au rôle PRESIDENT :
 * <ul>
 *   <li>messagerie directe président → agents/joueurs (horodatée et
 *       persistée côté communication-service, ouverture PRESIDENT validée
 *       par les tests MessagingSecurityTest) ;</li>
 *   <li>reçus PDF de salaires/primes émis vers un joueur ou un agent —
 *       le bénéficiaire ne voit que SES reçus (ownership serveur) ;</li>
 *   <li>réunions vidéo LiveKit via les composants Phase 5 réutilisés.</li>
 * </ul>
 * Thème clair blanc/rouge (tokens paper et ink) comme les espaces
 * joueur/staff — l'ADMIN garde son thème sombre.
 */
@Component({
  selector: 'app-president-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ErrorBannerComponent, MyCallsComponent, ScheduleCallFormComponent],
  templateUrl: './president-dashboard.component.html',
})
export class PresidentDashboardComponent implements OnInit {
  // ───────────────────────────── État général ─────────────────────────────
  loading = true;
  loadError = false;
  activeTab: 'messagerie' | 'recus' | 'video' | 'annuaire' = 'messagerie';

  api = inject(ApiService);
  auth = inject(AuthService);
  private toast = inject(ToastService);

  // ──────────────────── C.21 — Profil président (badge doré) ────────────────────
  /** Profil complet du président connecté (nom, discipline, statut). */
  profil: any = null;
  /** Libellé lisible de la discipline (« FOOTBALL » → « Football »). */
  get disciplineLabel(): string {
    const d = this.profil?.disciplineDemandee;
    if (!d) return '';
    return d.charAt(0).toUpperCase() + d.slice(1).toLowerCase();
  }
  /** Couleur dorée (GOL) appliquée au badge, propre au rôle président.
   * Distincte du rouge (wydad-red) et du neutre papier, pour qu'un visiteur
   * identifie immédiatement l'autorité présidentielle. */
  get badgeOr(): { bg: string; text: string; border: string } {
    return {
      bg: 'bg-gradient-to-br from-amber-300 via-yellow-400 to-amber-500',
      text: 'text-amber-950',
      border: 'border-amber-600/40',
    };
  }

  // ───────────────────────────── Messagerie ─────────────────────────────
  inbox: any[] = [];
  inboxLoading = false;
  selectedContactId: number | null = null;
  conversation: any[] = [];
  convLoading = false;
  newMessage = '';

  // C.21 — Annuaire des interlocuteurs autorisés (joueurs + entraîneurs de
  // MA discipline). Renforcé côté backend par TeamIsolationService.
  contactsDir: any[] = [];
  contactsDirLoading = false;

  // ───────────────────────────── Reçus ─────────────────────────────
  receipts: any[] = [];
  receiptsLoading = false;
  /** Plus de cache `users` local : le dropdown Bénéficiaire est peuplé
   *  depuis `contactsDir` (annuaire discipline, chargé au mount). Avant
   *  ce fix on appelait `getAllUsers()` → /auth/admin/users (réservé
   *  ADMIN) → 403 silencieux → dropdown vide côté président. */
  showEmissionForm = false;
  isSubmittingReceipt = false;

  emissionForm = {
    userId: null as number | null,
    receiptType: 'SALAIRE' as 'SALAIRE' | 'PRIME',
    amount: null as number | null,
    periode: '',
    motif: '',
  };

  ngOnInit() {
    this.loadProfil();
    this.loadAll();
    this.loadContactsDiscipline();
  }

  /**
   * C.21 — Charge le profil président (nom, discipline, statutCompte) pour
   * afficher le badge doré et le sélecteur de discipline. Le profil est
   * déjà mis en cache par AuthService.currentUser$ — on le lit directement
   * depuis le localStorage pour éviter un round-trip.
   */
  private loadProfil() {
    this.profil = {
      firstName: localStorage.getItem('wydad_first_name') || 'Président',
      lastName: localStorage.getItem('wydad_last_name') || '',
      email: localStorage.getItem('wydad_email'),
      disciplineDemandee: localStorage.getItem('wydad_discipline') || null,
      statutCompte: 'VALIDE',
    };
  }

  retryLoad() {
    this.loadAll();
  }

  private loadAll() {
    this.loading = true;
    this.loadError = false;
    let done = 0;
    const finish = () => {
      done++;
      if (done >= 2) {
        this.loading = false;
      }
    };
    this.loadInbox(finish);
    this.loadReceipts(finish);
  }

  // ═══════════════════════════ Messagerie ═══════════════════════════

  /**
   * C.21 — Charge l'annuaire des joueurs + entraîneurs de la discipline
   * du président. Backend = sports-service /players/filter et
   * /staff/filter avec sportType=discipline du président.
   *
   * <p>Si la discipline n'est pas connue (login trop ancien, pas de
   * disciplineDemandee en localStorage), on tente via /api/auth/me.</p>
   */
  /**
   * C.21 vague 3 — Pré-remplit le ScheduleCallFormComponent avec l'interlocuteur
   * cliqué : cible UTILISATEURS + targetUserIds=[id]. Le président n'a plus
   * qu'à saisir titre + date + durée et confirmer. Bascule sur l'onglet
   * Vidéo pour rendre le formulaire visible.
   */
  scheduleCallWith(contact: any) {
    this.callTargetUserIds = [contact.id];
    this.callTargetName = contact.name;
    this.activeTab = 'video';
    this.toast.show('info', `Programmez un appel avec ${contact.name} (titre, date, durée)`);
  }

  /** Liste des userIds à passer au ScheduleCallFormComponent (ng-template binding). */
  callTargetUserIds: number[] = [];
  callTargetName = '';

  loadContactsDiscipline() {
    const disc = this.profil?.disciplineDemandee;
    if (!disc) {
      // Récupère via /api/auth/me (méthode du auth.service).
      this.auth.getProfile().subscribe({
        next: (p: any) => {
          this.profil = { ...this.profil, disciplineDemandee: p?.disciplineDemandee };
          if (p?.disciplineDemandee) {
            localStorage.setItem('wydad_discipline', p.disciplineDemandee);
            this.loadContactsDiscipline();
          }
        }
      });
      return;
    }
    this.contactsDirLoading = true;
    // Charge en parallèle joueurs et entraîneurs.
    Promise.all([
      this.api.getPlayersByDiscipline(disc).toPromise(),
      this.api.getStaffByDiscipline(disc).toPromise()
    ]).then(([players, staff]) => {
      const merged: any[] = [];
      (players || []).forEach((p: any) => merged.push({
        id: p.userId, name: p.fullName, role: 'JOUEUR', category: p.category
      }));
      (staff || []).forEach((s: any) => merged.push({
        id: s.userId, name: s.fullName, role: 'ENTRAINEUR', category: s.assignedCategory
      }));
      this.contactsDir = merged;
      this.contactsDirLoading = false;
    }).catch(() => {
      this.contactsDirLoading = false;
    });
  }

  loadInbox(done?: () => void) {
    this.inboxLoading = true;
    this.api.getInbox().subscribe({
      next: (data) => {
        this.inbox = data;
        this.inboxLoading = false;
        done?.();
      },
      error: () => {
        this.inboxLoading = false;
        done?.();
        // Avant : `!this.users.length` (cache supprimé). On regarde
        // l'annuaire discipline comme signal de « vraiment rien à
        // montrer » pour ne pas afficher un loadError si le président
        // n'a juste aucun contact rattaché.
        if (!this.receipts.length && !this.contactsDir.length) {
          this.loadError = true;
        }
        this.toast.show('error', 'Impossible de charger la messagerie.');
      },
    });
  }

  selectContact(id: number) {
    this.selectedContactId = id;
    this.convLoading = true;
    this.api.getConversation(id).subscribe({
      next: (data) => {
        this.conversation = data;
        this.convLoading = false;
      },
      error: () => {
        this.conversation = [];
        this.convLoading = false;
        this.toast.show('error', 'Impossible de charger la conversation.');
      },
    });
  }

  sendToSelected() {
    const content = this.newMessage.trim();
    if (!this.selectedContactId || !content) return;
    this.api.sendMessage(this.selectedContactId, content).subscribe({
      next: () => {
        this.newMessage = '';
        this.selectContact(this.selectedContactId!);
        this.toast.show('success', 'Message envoyé.');
      },
      error: (e) => {
        this.toast.show('error', e?.error?.message || "Envoi impossible.");
      },
    });
  }

  /** Destinataires dédupliqués depuis la boîte de réception. */
  get contacts(): any[] {
    const map = new Map<number, any>();
    for (const m of this.inbox) {
      if (!map.has(m.senderUserId)) {
        map.set(m.senderUserId, {
          id: m.senderUserId,
          name: m.senderName,
          role: m.senderRole,
        });
      }
    }
    return [...map.values()];
  }

  formatTime(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleString('fr-FR', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
    });
  }

  // ═══════════════════════════ Reçus ═══════════════════════════

  loadReceipts(done?: () => void) {
    this.receiptsLoading = true;
    this.api.getSalaryReceipts().subscribe({
      next: (data) => {
        this.receipts = data;
        this.receiptsLoading = false;
        done?.();
      },
      error: () => {
        this.receiptsLoading = false;
        done?.();
        this.toast.show('error', 'Impossible de charger les reçus.');
      },
    });
  }

  openEmissionForm() {
    this.showEmissionForm = true;
    // Le dropdown Bénéficiaire itère désormais sur `contactsDir` (annuaire
    // discipline, déjà chargé au mount par `loadContactsDiscipline()`).
    // Pas de nouvel appel HTTP ici : ouvrir le formulaire n'a aucun coût.
    // Si l'annuaire est vide (discipline sans interlocuteur), le <select>
    // n'affiche que l'option « — Sélectionner… — » — l'utilisateur peut
    // annuler ou attendre un rechargement.
  }

  closeEmissionForm() {
    this.showEmissionForm = false;
    this.emissionForm = {
      userId: null, receiptType: 'SALAIRE', amount: null, periode: '', motif: '',
    };
  }

  submitReceipt() {
    const f = this.emissionForm;
    if (!f.userId || !f.amount || f.amount <= 0) {
      this.toast.show('error', 'Bénéficiaire et montant positif obligatoires.');
      return;
    }
    if (f.receiptType === 'SALAIRE' && !f.periode.trim()) {
      this.toast.show('error', 'La période est obligatoire pour un salaire.');
      return;
    }
    this.isSubmittingReceipt = true;
    this.api.emettreRecu({
      userId: f.userId,
      receiptType: f.receiptType,
      amount: f.amount,
      periode: f.periode.trim() || undefined,
      motif: f.motif.trim() || undefined,
    }).subscribe({
      next: (r) => {
        this.isSubmittingReceipt = false;
        this.closeEmissionForm();
        this.receipts = [r, ...this.receipts];
        this.toast.show('success', 'Reçu émis — le bénéficiaire peut le télécharger.');
      },
      error: (e) => {
        this.isSubmittingReceipt = false;
        this.toast.show('error', e?.error?.message || 'Émission impossible.');
      },
    });
  }

  downloadPdf(receipt: any) {
    this.api.getRecuPdf(receipt.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `recu-${receipt.reference}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.show('error', 'Téléchargement impossible.'),
    });
  }

  formatDate(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR');
  }
}
