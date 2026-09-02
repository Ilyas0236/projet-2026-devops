import { Component, OnInit, OnDestroy, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
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
export class DashboardJoueurComponent implements OnInit, OnDestroy {
  api = inject(ApiService);
  auth = inject(AuthService);
  private router = inject(Router);
  toast = inject(ToastService);

  player: any = null;
  sessions: any[] = [];
  loading = true;
  loadError = false;

  // Convocations / présence / documents / stats détaillées
  convocations: any[] = [];
  /** V1.3 — séances d'entraînement où le joueur est explicitement convoqué. */
  myConvokedSessions: any[] = [];
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
  // V2.3 — pièce jointe en cours d'envoi
  pendingAttachment: {
    publicId: string; secureUrl: string; resourceType: string;
    fileName: string; sizeBytes: number;
  } | null = null;
  uploadingAttachment = false;

  // Annonces visibles : club + ma catégorie (filtrage serveur)
  announcements: any[] = [];

  // B.11 : espace de notifications UNIQUE (convocations, médical, messages,
  // réponses du club…) — ownership prouvé côté serveur (assertSelfOrAdmin).
  notifications: any[] = [];
  unreadNotifications = 0;
  markingNotificationId: number | null = null;

  // C.21.v2 — Reçus de salaire/prime émis par le président du club.
  // Source : GET /api/auth/salary-receipts/mine (ownership strict côté serveur,
  // test mineNeRetourneQueSesPropresRecus). Téléchargement PDF via getRecuPdf
  // (IDOR strict, test unJoueurNePeutPasTelechargerLeRecuDUnAutre).
  myReceipts: any[] = [];
  myReceiptsLoading = false;
  /** Reçus non encore vus (id > lastSeenReceiptId_{userId} en localStorage). */
  unreadReceipts = 0;
  /** Valeur brute du lastSeenId lue depuis localStorage — affichée dans le template
   *  pour surligner les lignes non vues (bg-amber-50). Null au 1er accès. */
  lastSeenReceiptIdValue: number | null = null;
  /** ID du reçu en cours de téléchargement PDF (loading state sur le bouton). */
  downloadingReceiptId: number | null = null;

  // C.21.v2 — auto-mark des reçus comme vus quand la section devient visible.
  // On observe l'élément DOM rendu par le template (ref #mesRecusSection) ;
  // dès qu'il entre dans le viewport, on appelle markReceiptsSeen() pour
  // vider le badge. Plus naturel qu'un clic explicite.
  @ViewChild('mesRecusSection') mesRecusSectionRef?: ElementRef<HTMLElement>;
  private recuObserver: IntersectionObserver | null = null;

  // Transparence financière — rapports publiés par le club
  rapportsFinanciers: any[] = [];

  loadRapportsFinanciers() {
    this.api.getRapportsFinanciers().subscribe({
      next: (list) => (this.rapportsFinanciers = Array.isArray(list) ? list.slice(0, 3) : []),
      error: () => (this.rapportsFinanciers = [])
    });
  }

  // ═══════════════════════════ Reçus de salaire/prime (C.21.v2) ═══════════════════════════

  /**
   * C.21.v2 — Charge les reçus de l'utilisateur connecté via
   * {@code GET /api/auth/salary-receipts/mine}. Le serveur résout l'identité
   * à partir du header {@code X-User-Email} (la gateway réécrit ce header
   * avec l'email du JWT, donc impossible d'usurper l'identité d'un autre
   * user en l'injectant côté client). Puis on calcule le nombre de
   * "non vus" via {@link computeUnreadReceipts} (compare à lastSeen en
   * localStorage). Le badge s'affiche mais ne se vide que lorsque
   * l'utilisateur ouvre effectivement la section (cf. markReceiptsSeen).
   */
  loadMyReceipts() {
    this.myReceiptsLoading = true;
    this.api.getMySalaryReceipts().subscribe({
      next: (data) => {
        this.myReceipts = Array.isArray(data) ? data : [];
        this.myReceiptsLoading = false;
        this.computeUnreadReceipts();
        // Attend le prochain tick pour que @ViewChild('mesRecusSection') soit lié au DOM.
        setTimeout(() => this.observeMesRecusSection(), 0);
      },
      error: () => {
        this.myReceiptsLoading = false;
        this.toast.error('Impossible de charger vos reçus.');
      },
    });
  }

  /**
   * C.21.v2 — Calcule le nombre de reçus non vus pour l'utilisateur courant.
   * <p>Logique :</p>
   * <ul>
   *   <li>Lit {@code localStorage.lastSeenReceiptId_{userId}} (entier ou absent).</li>
   *   <li>Si absent (1er accès, nouveau navigateur, cache vidé) → tous les
   *       reçus sont considérés comme non vus.</li>
   *   <li>Sinon → on compte les reçus dont {@code id > lastSeenId}.</li>
   * </ul>
   * Le compteur n'est affiché que dans l'en-tête de la section (badge
   * inline). Il ne se vide pas automatiquement — c'est
   * {@link markReceiptsSeen} qui le fait, sur action explicite.
   */
  computeUnreadReceipts() {
    const userId = this.auth.getCurrentUserId();
    if (!userId) {
      this.unreadReceipts = 0;
      this.lastSeenReceiptIdValue = null;
      return;
    }
    const key = `lastSeenReceiptId_${userId}`;
    const raw = (() => {
      try { return localStorage.getItem(key); } catch { return null; }
    })();
    const lastSeen = raw ? Number(raw) : null;
    this.lastSeenReceiptIdValue = lastSeen;
    if (lastSeen == null || Number.isNaN(lastSeen)) {
      this.unreadReceipts = this.myReceipts.length;
    } else {
      this.unreadReceipts = this.myReceipts.filter((r) => r.id > lastSeen).length;
    }
  }

  /**
   * C.21.v2 — Marque tous les reçus courants comme vus (clic sur l'en-tête
   * de la section OU après 5 s de focus sur la page, au choix du parent).
   * Écrit {@code lastSeenReceiptId_{userId}} = max(id) et remet le compteur
   * à 0. Sans effet si la liste est vide.
   */
  markReceiptsSeen() {
    if (this.myReceipts.length === 0) {
      this.unreadReceipts = 0;
      return;
    }
    const userId = this.auth.getCurrentUserId();
    if (!userId) return;
    const maxId = Math.max(...this.myReceipts.map((r) => r.id));
    try {
      localStorage.setItem(`lastSeenReceiptId_${userId}`, String(maxId));
    } catch {
      // localStorage peut être désactivé (private mode) — on n'échoue pas l'UX.
    }
    this.lastSeenReceiptIdValue = maxId;
    this.unreadReceipts = 0;
  }

  /**
   * C.21.v2 — Téléchargement du PDF officiel d'un reçu. Pattern copié
   * du dashboard président ({@link downloadPdf} côté président). Le bouton
   * se met en état loading via {@code downloadingReceiptId}.
   */
  downloadReceiptPdf(receipt: any) {
    if (!receipt?.id) return;
    this.downloadingReceiptId = receipt.id;
    this.api.getRecuPdf(receipt.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `recu-${receipt.reference}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.downloadingReceiptId = null;
      },
      error: () => {
        this.downloadingReceiptId = null;
        this.toast.error('Téléchargement du PDF impossible.');
      },
    });
  }

  /** Format court d'une date ISO pour le tableau (fr-FR dd/MM/yyyy). */
  formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('fr-FR');
  }

  /**
   * C.21.v2 — Quand la section "Mes reçus" entre dans le viewport, on
   * déclenche {@link markReceiptsSeen} pour vider le badge. On observe
   * une fois (rootMargin: 0px, threshold: 0.1) puis on disconnect pour
   * éviter de re-déclencher à chaque scroll.
   */
  private observeMesRecusSection() {
    if (!this.mesRecusSectionRef?.nativeElement) return;
    if (this.recuObserver) return;
    this.recuObserver = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            this.markReceiptsSeen();
            this.recuObserver?.disconnect();
            this.recuObserver = null;
            break;
          }
        }
      },
      { threshold: 0.1 }
    );
    this.recuObserver.observe(this.mesRecusSectionRef.nativeElement);
  }

  ngOnDestroy() {
    this.recuObserver?.disconnect();
    this.recuObserver = null;
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
        this.loadTeammates();
        this.loadMyReceipts();
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
    // §8 — MES convocations de MATCH (titulaire/remplaçant), ownership serveur.
    this.api.getMyMatchConvocations().subscribe({
      next: d => this.matchConvocations = Array.isArray(d) ? d : [],
      error: () => {}
    });
    // V1.3 — séances d'entraînement où JE suis convoqué (sélection entraineur).
    this.api.getMyConvokedSessions().subscribe({
      next: d => this.myConvokedSessions = Array.isArray(d) ? d : [],
      error: () => {}
    });
    this.loadNotifications();
  }

  /** §8 — convocations de match du joueur (distinctes des convocations de séance). */
  matchConvocations: any[] = [];

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

  /**
   * Phase 5 — suit le lien profond d'une notification (targetUrl émis par les
   * services : /joueur/dashboard, /staff/dashboard…). Sans lien : simple « Lu ».
   */
  openNotification(n: any) {
    if (n.status !== 'READ') { this.markNotificationRead(n); }
    const url = n.targetUrl as string | undefined;
    if (url && url.startsWith('/')) { this.router.navigateByUrl(url); }
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
    this.pendingAttachment = null;
  }

  /** V2.3 — sélection d'un fichier à joindre au prochain message. */
  onAttachmentSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    const file = input.files[0];
    if (file.size > 10 * 1024 * 1024) {
      this.toast.error('Pièce jointe trop volumineuse (max. 10 Mo).');
      input.value = '';
      return;
    }
    this.uploadingAttachment = true;
    this.api.uploadMessageAttachment(file).subscribe({
      next: (res) => {
        this.pendingAttachment = {
          publicId: res.publicId,
          secureUrl: res.secureUrl,
          resourceType: res.resourceType,
          fileName: res.fileName,
          sizeBytes: res.sizeBytes
        };
        this.uploadingAttachment = false;
        input.value = '';
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Upload impossible');
        this.uploadingAttachment = false;
        input.value = '';
      }
    });
  }

  /** V2.3 — annule la pièce jointe en attente. */
  cancelAttachment() {
    this.pendingAttachment = null;
  }

  /** V2.3 — récupère une URL signée fraîche pour afficher la pièce jointe. */
  getAttachment(messageId: number, callback: (url: string) => void) {
    this.api.getMessageAttachmentUrl(messageId).subscribe({
      next: (r) => callback(r.url),
      error: () => callback('')
    });
  }

  sendMessageToStaff() {
    if (!this.conversationWith) { return; }
    if (!this.messageDraft.trim() && !this.pendingAttachment) { return; }
    this.sendingMessage = true;
    const myId = Number(this.auth.getCurrentUserId());
    const draft = this.messageDraft.trim();
    const att = this.pendingAttachment || undefined;
    this.api.sendMessage(this.conversationWith.id, draft, att).subscribe({
      next: () => {
        // Optimiste local : le serveur a validé l'appariement avant de persister
        this.conversation = [...this.conversation, {
          senderUserId: myId,
          recipientUserId: this.conversationWith!.id,
          content: draft,
          attachmentPublicId: att?.publicId,
          attachmentFileName: att?.fileName,
          attachmentResourceType: att?.resourceType,
          attachmentSecureUrl: att?.secureUrl,
          attachmentSizeBytes: att?.sizeBytes,
          createdAt: new Date().toISOString()
        }];
        this.messageDraft = '';
        this.pendingAttachment = null;
        this.sendingMessage = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Envoi impossible');
        this.sendingMessage = false;
      }
    });
  }

  // ───────────────────── Mon équipe (coéquipiers) ─────────────────────
  // Phase 4 — joueurs de MON sport+catégorie (même groupe). La conversation
  // individuelle réutilise la messagerie existante ; l'appel vidéo cible
  // EQUIPE_JOUEURS (le serveur force le groupe depuis MA fiche, jamais les
  // paramètres du client).

  teammates: any[] = [];
  loadingTeammates = false;
  creatingTeamCall = false;

  /** La conversation ouverte est un coéquipier (affichage du renvoi messagerie). */
  get isTeammateConversation(): boolean {
    return !!this.conversationWith && this.teammates.some(t => t.userId === this.conversationWith!.id);
  }

  /** Liste mes coéquipiers via l'endpoint filtré par mon propre groupe. */
  loadTeammates() {
    if (!this.player?.sportType || !this.player?.category) { return; }
    this.loadingTeammates = true;
    this.api.getPlayersByCategory(this.player.sportType, this.player.category).subscribe({
      next: (list) => {
        const myId = Number(this.auth.getCurrentUserId());
        this.teammates = (Array.isArray(list) ? list : [])
          .filter((p: any) => Number(p.userId) !== myId);
        this.loadingTeammates = false;
      },
      error: () => { this.teammates = []; this.loadingTeammates = false; }
    });
  }

  /**
   * Programme un appel pour MES coéquipiers (cible EQUIPE_JOUEURS).
   * Sport/catégorie volontairement omis : forcés côté serveur depuis ma fiche.
   */
  createTeamCall() {
    const title = window.prompt('Titre de l\'appel (ex : « Point tactique avant samedi ») :') || '';
    if (!title.trim()) { return; }
    this.creatingTeamCall = true;
    this.api.createCall({
      title: title.trim(),
      durationMinutes: 30,
      target: 'EQUIPE_JOUEURS'
    }).subscribe({
      next: () => {
        this.toast.success('Appel d\'équipe programmé — vos coéquipiers sont notifiés');
        this.creatingTeamCall = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Programmation impossible');
        this.creatingTeamCall = false;
      }
    });
  }

  /** Fait défiler jusqu'à la messagerie où la conversation s'affiche. */
  scrollMessagerie() {
    setTimeout(() => {
      document.getElementById('messagerie-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
