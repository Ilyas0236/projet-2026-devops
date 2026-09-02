import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';
import { MyCallsComponent } from '../../../components/my-calls/my-calls.component';
import { ScheduleCallFormComponent } from '../../../components/my-calls/schedule-call-form.component';
import { TeamChatComponent } from '../../../components/team-chat/team-chat.component';
import { ToastService } from '../../../services/toast.service';

/**
 * Espace Entraîneur — destination du login pour le rôle ENTRAINEUR
 * (correctif bouton « Se connecter », §27). Vue de pilotage 100% réelle :
 * effectif et séances de SA discipline+catégorie (isolation §6/§24),
 * matchs programmés de sa catégorie, convocations match (§8/§9) :
 * sélection de joueurs, choix titulaire/remplaçant, soumission à l'ADMIN
 * pour publication sur le site officiel. Appels vidéo LiveKit (Phase 5).
 * Thème clair Club (tokens paper/ink).
 */
@Component({
  selector: 'app-dashboard-entraineur',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent, MyCallsComponent, ScheduleCallFormComponent, TeamChatComponent],
  templateUrl: './dashboard-entraineur.component.html'
})
export class DashboardEntraineurComponent implements OnInit, OnDestroy {
  loading = true;
  loadError = false;
  activeTab: 'effectif' | 'seances' | 'matchs' | 'messagerie' | 'video' | 'recus' | 'vip' = 'effectif';

  api = inject(ApiService);
  auth = inject(AuthService);
  private toast = inject(ToastService);

  /** Fiche staff rattachée au compte entraîneur (sports-service). */
  staff: any = null;
  joueurs: any[] = [];
  seances: any[] = [];
  matchs: any[] = [];

  // ─── V1.2 — Convocations de match ───
  /** Match ouvert dans le drawer de convocation (id Match content-service). */
  selectedMatchId: number | null = null;
  /** Joueurs de la catégorie récupérés via /selectable (isolation §24). */
  selectablePlayers: any[] = [];
  /** Feuille de match existante (titulaires + remplaçants, statuts). */
  existingSheet: any[] = [];
  /** Brouillon local de la feuille en cours d'édition : userId -> 'TITULAIRE' | 'REMPLACANT' | absent. */
  convocationDraft: Map<number, 'TITULAIRE' | 'REMPLACANT'> = new Map();
  /** Map joueurUserId -> jerseyNumber pour l'affichage de la feuille. */
  jerseyByUser: Record<number, number> = {};
  loadingConvocations = false;
  submittingSheet = false;

  // ─── C.21.v2 — Reçus de salaire/prime reçus du président ───
  myReceipts: any[] = [];
  myReceiptsLoading = false;
  unreadReceipts = 0;
  lastSeenReceiptIdValue: number | null = null;
  downloadingReceiptId: number | null = null;

  ngOnInit() {
    this.loadAll();
  }

  retryLoad() {
    this.loadAll();
  }

  private loadAll() {
    this.loading = true;
    this.loadError = false;
    const userId = this.auth.getCurrentUserId();
    if (!userId) {
      this.loadError = true;
      this.loading = false;
      return;
    }

    // B.29 — billets VIP du membre connecté (indépendants de la fiche staff
    // : un coach SENIOR rattaché à un autre groupe peut quand même avoir
    // reçu 4 billets pour un match où il officie).
    this.loadVipTickets(userId);

    this.api.getStaffByUserId(userId).subscribe({
      next: (staff) => {
        this.staff = staff;
        this.chargerDonneesCategorie();
      },
      error: () => {
        // Pas de fiche staff : le compte existe mais n'est rattaché à
        // aucune discipline/catégorie — écran vide assumé, jamais simulé.
        this.staff = null;
        this.loading = false;
      }
    });
  }

  private chargerDonneesCategorie() {
    const sport = this.staff?.sportType as string | undefined;
    const categorie = (this.staff?.assignedCategory || this.staff?.category) as string | undefined;
    let remaining = sport && categorie ? 3 : 1;

    const finish = () => {
      if (--remaining <= 0) this.loading = false;
    };

    if (!sport || !categorie) {
      finish();
      return;
    }

    this.api.getPlayersByCategory(sport, categorie).subscribe({
      next: (list) => {
        this.joueurs = list || [];
        // Cache local userId -> n° de maillot pour l'affichage feuille
        this.jerseyByUser = {};
        for (const j of this.joueurs) {
          if (j.userId != null && j.shirtNumber != null) {
            this.jerseyByUser[j.userId] = j.shirtNumber;
          }
        }
        finish();
      },
      error: () => { this.joueurs = []; finish(); }
    });

    this.api.getSessionsByCategory(sport, categorie).subscribe({
      next: (list) => { this.seances = list || []; finish(); },
      error: () => { this.seances = []; finish(); }
    });

    this.api.getMatches().subscribe({
      next: (list) => {
        // Isolation §6 : seuls les matchs de SA discipline+catégorie.
        this.matchs = (list || []).filter(m =>
          (!m.sport || m.sport === sport) &&
          (!m.categorie || m.categorie === categorie));
        finish();
      },
      error: () => { this.matchs = []; finish(); }
    });

    this.loadMyReceipts();
  }

  formatSeance(seance: any): string {
    if (!seance?.date) return '';
    const d = new Date(`${seance.date}T${seance.heure || '00:00'}`);
    return isNaN(d.getTime()) ? String(seance.date)
      : `${d.toLocaleDateString('fr-FR')} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  formatMatchDate(match: any): string {
    return this.formatSeance(match);
  }

  // ─── V1.2 — Convocations de match ───

  /**
   * Ouvre le drawer de convocation pour un match : charge (1) la liste des
   * joueurs sélectionnables (isolation serveur §24) et (2) la feuille
   * éventuellement déjà existante (pour repartir de la sélection courante
   * si on revient dessus).
   */
  openConvocation(matchId: number) {
    this.selectedMatchId = matchId;
    this.convocationDraft = new Map();
    this.existingSheet = [];
    this.selectablePlayers = [];
    this.loadingConvocations = true;

    let pending = 2;
    const done = () => { if (--pending === 0) this.loadingConvocations = false; };

    this.api.getSelectablePlayers(matchId).subscribe({
      next: (list) => {
        this.selectablePlayers = list || [];
        done();
      },
      error: () => { this.selectablePlayers = []; done(); }
    });

    this.api.getMatchSheet(matchId).subscribe({
      next: (sheet) => {
        this.existingSheet = sheet || [];
        // Réhydrater le brouillon à partir de la feuille existante pour
        // qu'un entraîneur qui revient retrouve sa sélection.
        for (const row of this.existingSheet) {
          if (row.joueurUserId != null && row.playerRole) {
            this.convocationDraft.set(row.joueurUserId, row.playerRole);
          }
        }
        done();
      },
      error: () => { this.existingSheet = []; done(); }
    });
  }

  closeConvocation() {
    this.selectedMatchId = null;
    this.selectablePlayers = [];
    this.existingSheet = [];
    this.convocationDraft = new Map();
  }

  /**
   * Coche/décoche un joueur avec un rôle. Si on re-clique sur le même rôle,
   * on désélectionne (toggle). Permet de bâtir la feuille match par match
   * sans avoir à "déplacer" une sélection.
   */
  togglePlayerRole(userId: number, role: 'TITULAIRE' | 'REMPLACANT') {
    const current = this.convocationDraft.get(userId);
    if (current === role) {
      this.convocationDraft.delete(userId);
    } else {
      this.convocationDraft.set(userId, role);
    }
  }

  isPlayerSelected(userId: number, role: 'TITULAIRE' | 'REMPLACANT'): boolean {
    return this.convocationDraft.get(userId) === role;
  }

  countTitulaires(): number {
    let n = 0;
    for (const r of this.convocationDraft.values()) if (r === 'TITULAIRE') n++;
    return n;
  }

  countRemplacants(): number {
    let n = 0;
    for (const r of this.convocationDraft.values()) if (r === 'REMPLACANT') n++;
    return n;
  }

  /**
   * Enregistre le brouillon de feuille (sans soumettre à l'ADMIN). Permet à
   * l'entraîneur de travailler en plusieurs sessions sans perdre sa
   * sélection. Le back ne déduplique pas : on n'envoie que les nouvelles
   * entrées, le PATCH du back conserve les existantes.
   */
  saveDraft() {
    if (!this.selectedMatchId || this.convocationDraft.size === 0) {
      this.toast.error('Sélectionnez au moins un joueur.');
      return;
    }
    const players: { joueurUserId: number; playerRole: string }[] = [];
    this.convocationDraft.forEach((role, userId) => {
      players.push({ joueurUserId: userId, playerRole: role });
    });
    this.api.convocateBatchForMatch(this.selectedMatchId, players).subscribe({
      next: () => this.toast.success(`Brouillon enregistré : ${players.length} joueur(s).`),
      error: (err) => this.toast.error(err?.error?.message || 'Échec de l\'enregistrement du brouillon.')
    });
  }

  /**
   * Soumet la feuille à l'ADMIN pour publication (§9). Le back passe
   * toutes les convocations du match en statut PENDING ; l'ADMIN publie
   * ensuite via /admin/match/{id}/publish, et la feuille apparaît
   * publiquement sur le site officiel.
   */
  submitSheet() {
    if (!this.selectedMatchId) return;
    if (this.convocationDraft.size === 0) {
      this.toast.error('Sélectionnez au moins un joueur avant de soumettre.');
      return;
    }
    this.submittingSheet = true;
    // On enregistre d'abord le brouillon (idempotent) puis on soumet.
    const players: { joueurUserId: number; playerRole: string }[] = [];
    this.convocationDraft.forEach((role, userId) => {
      players.push({ joueurUserId: userId, playerRole: role });
    });

    this.api.convocateBatchForMatch(this.selectedMatchId, players).subscribe({
      next: () => {
        // Capture de la valeur pour rassurer TypeScript sur la non-nullité :
        // on est dans un callback async, selectedMatchId peut avoir été
        // réinitialisé entre temps (fermeture drawer, etc.).
        const matchId = this.selectedMatchId;
        if (matchId == null) {
          this.submittingSheet = false;
          return;
        }
        this.api.submitMatchSheet(matchId).subscribe({
          next: () => {
            this.toast.success('Feuille soumise à l\'administration pour publication.');
            this.submittingSheet = false;
            this.closeConvocation();
          },
          error: (err) => {
            this.toast.error(err?.error?.message || 'Échec de la soumission.');
            this.submittingSheet = false;
          }
        });
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Échec de l\'enregistrement de la feuille.');
        this.submittingSheet = false;
      }
    });
  }

  // ─── C.21.v2 — Reçus de salaire/prime reçus du président ───

  /**
   * Charge les reçus de salaire/prime émis à mon intention par le président.
   * L'endpoint /api/auth/salary-receipts/mine est résolu côté serveur via
   * le header X-User-Email de la gateway (pas de risque d'usurpation côté
   * client : impossible d'injecter un autre userId).
   */
  loadMyReceipts() {
    this.myReceiptsLoading = true;
    this.api.getMySalaryReceipts().subscribe({
      next: (data) => {
        this.myReceipts = Array.isArray(data) ? data : [];
        this.myReceiptsLoading = false;
        this.computeUnreadReceipts();
      },
      error: () => {
        this.myReceiptsLoading = false;
        this.toast.error('Impossible de charger vos reçus.');
      }
    });
  }

  /**
   * Compte les reçus non-vus (id > lastSeenId en localStorage).
   * Si l'user n'a jamais visité l'onglet → tous les reçus sont non-vus.
   */
  computeUnreadReceipts() {
    const userId = this.auth.getCurrentUserId();
    if (!userId) {
      this.unreadReceipts = 0;
      return;
    }
    const key = 'lastSeenReceiptId_' + userId;
    const stored = localStorage.getItem(key);
    if (stored == null) {
      // Premier accès : badge = nombre de reçus
      this.unreadReceipts = this.myReceipts.length;
      this.lastSeenReceiptIdValue = null;
      return;
    }
    const lastSeen = parseInt(stored, 10);
    this.lastSeenReceiptIdValue = isNaN(lastSeen) ? null : lastSeen;
    this.unreadReceipts = this.myReceipts.filter(r =>
      this.lastSeenReceiptIdValue == null || r.id > this.lastSeenReceiptIdValue
    ).length;
  }

  /**
   * Marque tous les reçus comme vus. Déclenché par le clic sur le bouton
   * d'onglet "Reçus" (voir template : `(click)="activeTab = 'recus'; markReceiptsSeen()"`).
   */
  markReceiptsSeen() {
    if (this.myReceipts.length === 0) return;
    const userId = this.auth.getCurrentUserId();
    if (!userId) return;
    const maxId = Math.max(...this.myReceipts.map(r => r.id));
    localStorage.setItem('lastSeenReceiptId_' + userId, String(maxId));
    this.lastSeenReceiptIdValue = maxId;
    this.unreadReceipts = 0;
  }

  /**
   * Télécharge le PDF d'un reçu. Pattern copié sur
   * dashboard-joueur.component.ts:downloadVipPdf.
   */
  downloadReceiptPdf(receipt: any) {
    if (this.downloadingReceiptId != null) return;
    this.downloadingReceiptId = receipt.id;
    this.api.getRecuPdf(receipt.id).subscribe({
      next: (blob: Blob) => {
        try {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `recu-${receipt.reference || receipt.id}.pdf`;
          document.body.appendChild(a);
          a.click();
          a.remove();
          window.URL.revokeObjectURL(url);
          this.toast.success('Reçu téléchargé.');
        } catch (e) {
          this.toast.error('Téléchargement impossible.');
        } finally {
          this.downloadingReceiptId = null;
        }
      },
      error: (err) => {
        this.downloadingReceiptId = null;
        this.toast.error(err?.error?.message || 'Téléchargement du PDF impossible.');
      }
    });
  }

  // ─── B.29 — Billets VIP offerts par match à domicile (SENIOR) ───
  // L'ADMIN distribue 4 billets VIP par membre SENIOR (joueur+staff+entraineur)
  // à chaque match à domicile. L'entraîneur SENIOR reçoit donc 4 billets comme
  // tout le monde, affichés ici sur le même modèle que le dashboard joueur.
  vipTickets: any[] = [];
  vipTicketsLoading = false;
  vipDownloadingId: number | null = null;

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

  formatDate(iso: string | null | undefined): string {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? String(iso) : d.toLocaleDateString('fr-FR');
  }

  ngOnDestroy() {
    // Pas d'observer côté entraineur : le mark se fait via clic onglet.
  }
}
