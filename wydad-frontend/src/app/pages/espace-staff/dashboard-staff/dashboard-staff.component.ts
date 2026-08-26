import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { TeamChatComponent } from '../../../components/team-chat/team-chat.component';
import { MyCallsComponent } from '../../../components/my-calls/my-calls.component';
import { ScheduleCallFormComponent } from '../../../components/my-calls/schedule-call-form.component';

@Component({
  selector: 'app-dashboard-staff',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule, TeamChatComponent, MyCallsComponent, ScheduleCallFormComponent],
  templateUrl: './dashboard-staff.component.html'
})
export class DashboardStaffComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  fb = inject(FormBuilder);
  toast = inject(ToastService);

  staff: any = null;
  players: any[] = [];
  sessions: any[] = [];
  loading = true;
  staffNotFound = false;
  /** Phase 5 — le président n'a pas de fiche staff : vue réduite aux appels programmés. */
  isPresident = false;
  /**
   * Phase 5 — la programmation d'appels est réservée ENTRAINEUR/PRESIDENT/ADMIN
   * côté backend (@PreAuthorize ScheduledCallController) ; on masque le
   * formulaire au STAFF simple (médecin, kiné…) pour éviter un 403 à la soumission.
   */
  canScheduleCalls = false;

  /** Phase 5 — rafraîchit l'agenda des appels après une programmation. */
  onCallCreated() {
    // Le composant enfant recharge lui-même ses données ; hook laissé pour un futur badge.
  }

  sessionForm!: FormGroup;
  isSubmitting = false;
  showForm = false;

  // B.4 — saisie de statistique de match pour un joueur de l'effectif
  statPlayer: any = null;          // joueur sélectionné
  showStatForm = false;
  isSubmittingStat = false;
  statForm!: FormGroup;

  // B.3.a — convocation d'un joueur pour une séance
  convoPlayer: any = null;
  isSubmittingConvo = false;

  // Phase 3 — convocation groupée (« liste cochable ») + suivi des réponses
  showConvoForm = false;
  selectedPlayerIds = new Set<number>();
  convoSessionId: number | null = null;
  isSubmittingBatch = false;
  // Suivi par séance : réponses + compteurs
  trackedSessionId: number | null = null;
  sessionResponses: any[] = [];
  sessionSummary: any = null;
  loadingResponses = false;

  // Phase 3 — envoi de médias tactiques (upload réel Cloudinary)
  showMediaForm = false;
  mediaFile: File | null = null;
  mediaTitle = '';
  mediaMessage = '';
  /** Joueur pré-ciblé (icône vidéo du tableau) — null = envoi équipe. */
  mediaTargetPlayer: any = null;
  isSubmittingMedia = false;
  sentMedia: any[] = [];

  // B.5 — messagerie (écrire aux joueurs de SA catégorie) et annonces
  inbox: any[] = [];
  conversation: any[] = [];
  conversationWith: { id: number; name: string } | null = null;
  messageDraft = '';
  sendingMessage = false;
  showAnnouncementForm = false;
  isSubmittingAnnouncement = false;
  announcementForm!: FormGroup;

  // B.6 — statut médical : réservé au staff médical (contrôle serveur)
  isMedicalStaff = false;
  medicalPlayer: any = null;

  // §8/§9 — feuille de match : convocations liées à un match RÉEL avec
  // titulaire/remplaçant, soumises à l'Admin pour publication publique.
  showMatchSheetForm = false;
  programmeMatches: any[] = [];       // matchs PROGRAMME de SA discipline+catégorie
  selectedMatchId: number | null = null;
  selectableMatchPlayers: any[] = [];
  /** userId → 'TITULAIRE' | 'REMPLACANT' */
  matchRoles = new Map<number, string>();
  existingSheet: any[] = [];          // feuille déjà enregistrée pour ce match
  isLoadingMatchPlayers = false;
  isSubmittingMatchSheet = false;
  matchSheetMessage = '';

  // Transparence financière — rapports publiés par le club
  rapportsFinanciers: any[] = [];

  loadRapportsFinanciers() {
    this.api.getRapportsFinanciers().subscribe({
      next: (list) => (this.rapportsFinanciers = Array.isArray(list) ? list.slice(0, 3) : []),
      error: () => (this.rapportsFinanciers = [])
    });
  }
  showMedicalForm = false;
  isSubmittingMedical = false;
  medicalNoteDraft = '';

  ngOnInit() {
    this.sessionForm = this.fb.group({
      title: ['', Validators.required],
      description: [''],
      location: ['', Validators.required],
      sessionDate: ['', Validators.required]
    });
    this.statForm = this.fb.group({
      opponent: ['', Validators.required],
      matchDate: ['', Validators.required],
      goals: [0, [Validators.required, Validators.min(0)]],
      assists: [0, [Validators.required, Validators.min(0)]],
      minutesPlayed: [null],
      competition: ['']
    });
    this.announcementForm = this.fb.group({
      title: ['', Validators.required],
      body: ['', Validators.required],
      scope: ['category']   // 'club' (tout le club) ou 'category' (sa catégorie)
    });

    // Charger le profil staff depuis le backend via l'ID utilisateur connecté
    const userId = this.auth.getCurrentUserId();
    const tokenRole = this.auth.getTokenRole();
    this.isPresident = tokenRole === 'PRESIDENT';
    this.canScheduleCalls = tokenRole === 'ENTRAINEUR'
        || tokenRole === 'PRESIDENT' || tokenRole === 'ADMIN';
    if (userId) {
      this.api.getStaffByUserId(userId).subscribe({
        next: (data) => {
          this.staff = data;
          // B.6 — le bouton médical n'apparaît que pour DOCTOR/PHYSIOTHERAPIST ;
          // le serveur revalide de toute façon (403 sinon).
          this.isMedicalStaff =
            data.role === 'DOCTOR' || data.role === 'PHYSIOTHERAPIST';
          this.loadDashboardData();
          this.loadRapportsFinanciers();
        },
        error: (err) => {
          console.error('Profil staff non trouvé', err);
          // Le président sans fiche staff a quand même accès aux appels programmés.
          this.staffNotFound = !this.isPresident;
          this.loading = false;
        }
      });
    } else {
      this.staffNotFound = true;
      this.loading = false;
    }
  }

  loadDashboardData() {
    const sport = this.staff.sportType;
    const category = this.staff.assignedCategory || this.staff.category;

    this.api.getPlayersByCategory(sport, category).subscribe({
      next: (data) => {
        this.players = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });

    this.api.getSessionsByCategory(sport, category).subscribe({
      next: (data) => {
        this.sessions = data;
      },
      error: (err) => console.error(err)
    });

    this.api.getInbox().subscribe({ next: d => this.inbox = d, error: () => {} });

    // Phase 3 — historique des médias émis
    this.api.getSentMedia().subscribe({
      next: d => this.sentMedia = d,
      error: () => {}
    });

    // §8 — matchs PROGRAMME de SA discipline + catégorie (source content-service).
    // Le filtrage local n'est qu'un confort : le serveur revalide de toute façon (403 sinon).
    this.api.getMatchesByStatut('PROGRAMME').subscribe({
      next: (list) => {
        this.programmeMatches = (Array.isArray(list) ? list : []).filter(m =>
          (!m.sport || m.sport === sport) && (!m.categorie || m.categorie === category));
      },
      error: () => {}
    });
  }

  // ─────────────── §8/§9 — Feuille de match (convocations liées à un match) ───────────────

  toggleMatchSheetForm() {
    this.showMatchSheetForm = !this.showMatchSheetForm;
    if (!this.showMatchSheetForm) { return; }
    this.selectedMatchId = null;
    this.selectableMatchPlayers = [];
    this.matchRoles.clear();
    this.existingSheet = [];
    this.matchSheetMessage = '';
  }

  /** Sélection d'un match : charge les joueurs du groupe + la feuille existante. */
  onMatchSelected() {
    if (!this.selectedMatchId) { return; }
    const matchId = Number(this.selectedMatchId);
    this.isLoadingMatchPlayers = true;
    this.matchRoles.clear();
    this.existingSheet = [];
    this.matchSheetMessage = '';

    this.api.getSelectablePlayers(matchId).subscribe({
      next: (players) => {
        this.selectableMatchPlayers = players;
        this.isLoadingMatchPlayers = false;
      },
      error: (err) => {
        console.error(err);
        this.isLoadingMatchPlayers = false;
        this.toast.error(err?.error?.message || 'Chargement des joueurs impossible');
      }
    });

    // Feuille déjà créée pour ce match (DRAFT/SOUMISE/PUBLIEE) → pré-remplissage.
    this.api.getMatchSheet(matchId).subscribe({
      next: (sheet) => {
        this.existingSheet = sheet;
        sheet.forEach((c: any) => {
          this.matchRoles.set(c.joueurUserId, c.playerRole);
        });
        const status = sheet[0]?.status;
        if (status === 'SOUMISE') {
          this.matchSheetMessage = 'Feuille déjà soumise à l\'Admin — en attente de validation.';
        } else if (status === 'PUBLIEE') {
          this.matchSheetMessage = 'Feuille PUBLIÉE sur le site public.';
        } else if (status === 'REFUSEE') {
          this.matchSheetMessage = 'Feuille REFUSÉE par l\'Admin — corrigez-la puis resoumettez.';
        }
      },
      error: () => {} // pas encore de feuille pour ce match
    });
  }

  toggleMatchPlayer(userId: number) {
    if (this.matchRoles.has(userId)) {
      this.matchRoles.delete(userId);
    } else {
      this.matchRoles.set(userId, 'TITULAIRE');
    }
  }

  setMatchRole(userId: number, role: string) {
    this.matchRoles.set(userId, role);
  }

  get selectedMatch(): any | null {
    return this.programmeMatches.find(m => m.id === Number(this.selectedMatchId)) ?? null;
  }

  submitMatchSheet() {
    const matchId = this.selectedMatchId ? Number(this.selectedMatchId) : null;
    if (!matchId || this.matchRoles.size === 0) { return; }
    this.isSubmittingMatchSheet = true;
    const players = [...this.matchRoles.entries()].map(([joueurUserId, playerRole]) =>
      ({ joueurUserId, playerRole }));

    this.api.convocateBatchForMatch(matchId, players).subscribe({
      next: (res) => {
        // Soumission immédiate à l'Admin (workflow §9 complet en un geste).
        this.api.submitMatchSheet(matchId).subscribe({
          next: () => {
            this.isSubmittingMatchSheet = false;
            const rejectedCount = res?.rejected?.length ?? 0;
            if (rejectedCount > 0) {
              this.toast.error(`${res.created} convocation(s), ${rejectedCount} rejetée(s) — ` +
                res.rejected.map((r: any) => r.reason).join(' · '));
            } else {
              this.toast.success('Feuille de match enregistrée et soumise à l\'Admin');
            }
            this.onMatchSelected(); // recharge l'état (SOUMISE)
          },
          error: (err) => {
            this.isSubmittingMatchSheet = false;
            this.toast.error(err?.error?.message || 'Soumission à l\'Admin impossible');
          }
        });
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingMatchSheet = false;
        this.toast.error(err?.error?.message || 'Enregistrement de la feuille impossible');
      }
    });
  }

  submitSession() {
    if (this.sessionForm.invalid) return;
    this.isSubmitting = true;

    const sport = this.staff.sportType;
    const category = this.staff.assignedCategory || this.staff.category;

    const payload = {
      ...this.sessionForm.value,
      sportType: sport,
      category: category,
      createdByStaffId: this.staff.id
    };

    this.api.createSession(payload).subscribe({
      next: (res) => {
        this.sessions.push(res);
        this.sessions.sort((a, b) => new Date(a.sessionDate).getTime() - new Date(b.sessionDate).getTime());
        this.isSubmitting = false;
        this.showForm = false;
        this.sessionForm.reset({ location: '' });
      },
      error: (err) => {
        console.error(err);
        this.isSubmitting = false;
        this.toast.error(err?.error?.message || 'Erreur lors de la création de la séance');
      }
    });
  }

  // ───────────────── B.4 — Statistiques de match réelles ─────────────────

  openStatForm(player: any) {
    this.statPlayer = player;
    this.statForm.reset({ goals: 0, assists: 0 });
    this.showStatForm = true;
  }

  closeStatForm() {
    this.showStatForm = false;
    this.statPlayer = null;
  }

  submitStat() {
    if (this.statForm.invalid || !this.statPlayer) return;
    this.isSubmittingStat = true;
    this.api.addPlayerStat(this.statPlayer.userId, this.statForm.value).subscribe({
      next: () => {
        this.toast.success(`Stat enregistrée pour ${this.statPlayer.fullName}`);
        this.isSubmittingStat = false;
        this.closeStatForm();
        // Recharge l'effectif : les totaux sont agrégés côté serveur
        this.loadDashboardData();
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingStat = false;
        this.toast.error(err?.error?.message || 'Erreur lors de la saisie de la stat');
      }
    });
  }

  // ───────────────── B.3.a — Convocation d'un joueur ─────────────────

  convoquer(player: any, sessionId: number) {
    this.isSubmittingConvo = true;
    this.api.createConvocation(player.userId, sessionId).subscribe({
      next: () => {
        this.toast.success(`${player.fullName} convoqué — notification envoyée`);
        this.isSubmittingConvo = false;
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingConvo = false;
        this.toast.error(err?.error?.message || 'Convocation impossible');
      }
    });
  }

  // ─────────────── Phase 3 — Convocation groupée (« liste cochable ») ───────────────

  /** Joueurs aptes (un INAPTE serait rejeté par le serveur). */
  get joueursAptes(): any[] {
    return this.players.filter(p => p.medicalStatus !== 'INAPTE');
  }

  get tousSelectionnes(): boolean {
    return this.joueursAptes.length > 0
      && this.selectedPlayerIds.size >= this.joueursAptes.length;
  }

  /** Titre de la séance suivie (affichage du panneau de suivi). */
  get trackedSessionTitle(): string {
    return this.sessions.find(s => s.id === this.trackedSessionId)?.title ?? '';
  }

  togglePlayerSelection(userId: number) {
    if (this.selectedPlayerIds.has(userId)) {
      this.selectedPlayerIds.delete(userId);
    } else {
      this.selectedPlayerIds.add(userId);
    }
  }

  selectAllPlayers() {
    // Sélectionne/désélectionne les joueurs aptes uniquement.
    if (this.tousSelectionnes) {
      this.selectedPlayerIds.clear();
    } else {
      this.joueursAptes.forEach(p => this.selectedPlayerIds.add(p.userId));
    }
  }

  submitBatchConvocation() {
    if (!this.convoSessionId || this.selectedPlayerIds.size === 0) { return; }
    this.isSubmittingBatch = true;
    this.api.createBatchConvocation(this.convoSessionId, [...this.selectedPlayerIds]).subscribe({
      next: (res) => {
        this.isSubmittingBatch = false;
        const rejectedCount = res?.rejected?.length ?? 0;
        if (rejectedCount > 0) {
          this.toast.error(`${res.created} convocation(s) créée(s), ${rejectedCount} rejetée(s) — ` +
            res.rejected.map((r: any) => r.reason).join(' · '));
        } else {
          this.toast.success(`${res.created} convocation(s) créée(s) — notifications envoyées`);
        }
        this.showConvoForm = false;
        this.selectedPlayerIds.clear();
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingBatch = false;
        this.toast.error(err?.error?.message || 'Convocation groupée impossible');
      }
    });
  }

  // ───────────────── Phase 3 — Suivi des réponses d'une séance ─────────────────

  trackSession(sessionId: number) {
    this.trackedSessionId = sessionId;
    this.loadingResponses = true;
    this.api.getSessionSummary(sessionId).subscribe({
      next: s => { this.sessionSummary = s; this.loadingResponses = false; },
      error: () => { this.sessionSummary = null; this.loadingResponses = false; }
    });
    this.api.getSessionResponses(sessionId).subscribe({
      next: r => this.sessionResponses = r,
      error: () => { this.sessionResponses = []; this.loadingResponses = false; }
    });
  }

  // ───────────────── Phase 3 — Médias tactiques (upload Cloudinary) ─────────────────

  onMediaFileSelected(event: any) {
    const file: File | null = event?.target?.files?.[0] ?? null;
    if (!file) { return; }
    if (file.size > 25 * 1024 * 1024) {
      this.toast.error('Fichier trop volumineux (maximum 25 Mo)');
      event.target.value = '';
      return;
    }
    this.mediaFile = file;
  }

  submitMedia() {
    if (!this.mediaFile) {
      this.toast.error('Choisissez un fichier (vidéo, photo ou PDF, max 25 Mo)');
      return;
    }
    const wholeTeam = this.mediaTargetPlayer == null;
    const joueurUserId = wholeTeam ? undefined : Number(this.mediaTargetPlayer.userId);
    this.isSubmittingMedia = true;
    this.api.shareMedia(this.mediaFile, this.mediaTitle, this.mediaMessage, {
      joueurUserId, wholeTeam
    }).subscribe({
      next: () => {
        this.isSubmittingMedia = false;
        this.toast.success(wholeTeam
          ? 'Média envoyé à toute l\'équipe — notifications envoyées'
          : 'Média envoyé au joueur — notification envoyée');
        this.closeMediaForm();
        // Rafraîchit l'historique des envois
        this.api.getSentMedia().subscribe({ next: d => this.sentMedia = d });
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingMedia = false;
        const msg = err?.status === 413 ? 'Fichier trop volumineux (maximum 25 Mo)'
          : err?.error?.message || 'Envoi du média impossible';
        this.toast.error(msg);
      }
    });
  }

  openMediaForm() {
    this.showMediaForm = !this.showMediaForm;
    this.mediaFile = null;
    this.mediaTitle = '';
    this.mediaMessage = '';
    this.mediaTargetPlayer = null;
  }

  /** Ouvre le formulaire média pré-ciblé sur UN joueur (icône vidéo du tableau). */
  openMediaFormForPlayer(player: any) {
    this.showMediaForm = true;
    this.mediaFile = null;
    this.mediaTitle = '';
    this.mediaMessage = '';
    this.mediaTargetPlayer = player;
  }

  closeMediaForm() {
    this.showMediaForm = false;
    this.mediaFile = null;
    this.mediaTitle = '';
    this.mediaMessage = '';
    this.mediaTargetPlayer = null;
  }

  // ───────────────── B.6 — Statut médical ─────────────────

  openMedicalForm(player: any) {
    this.medicalPlayer = player;
    this.medicalNoteDraft = player.medicalNote || '';
    this.showMedicalForm = true;
  }

  closeMedicalForm() {
    this.showMedicalForm = false;
    this.medicalPlayer = null;
    this.medicalNoteDraft = '';
  }

  setPlayerMedicalStatus(status: 'APT' | 'INAPTE') {
    if (!this.medicalPlayer) { return; }
    const note = this.medicalNoteDraft.trim();
    if (status === 'INAPTE' && !note) {
      this.toast.error('Un motif est requis pour déclarer un joueur inapte');
      return;
    }
    this.isSubmittingMedical = true;
    this.api.setMedicalStatus(this.medicalPlayer.userId, status, note || undefined).subscribe({
      next: (res) => {
        this.toast.success(`Statut médical de ${this.medicalPlayer.fullName} : ${res.status}`);
        this.isSubmittingMedical = false;
        this.closeMedicalForm();
        this.loadDashboardData();
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingMedical = false;
        this.toast.error(err?.error?.message || 'Modification du statut médical impossible');
      }
    });
  }

  // ───────────────── B.5 — Messagerie et annonces ─────────────────

  openConversation(playerUserId: number, playerName: string) {
    this.conversationWith = { id: playerUserId, name: playerName };
    this.api.getConversation(playerUserId).subscribe({
      next: d => this.conversation = d,
      error: () => this.toast.error('Impossible de charger la conversation')
    });
  }

  closeConversation() {
    this.conversationWith = null;
    this.conversation = [];
    this.messageDraft = '';
  }

  sendMessageToPlayer() {
    if (!this.messageDraft.trim() || !this.conversationWith) { return; }
    this.sendingMessage = true;
    const myId = Number(this.auth.getCurrentUserId());
    this.api.sendMessage(this.conversationWith.id, this.messageDraft.trim()).subscribe({
      next: () => {
        // Le serveur a validé l'appariement (même catégorie) avant de persister
        this.conversation = [...this.conversation, {
          senderUserId: myId,
          recipientUserId: this.conversationWith!.id,
          content: this.messageDraft.trim(),
          createdAt: new Date().toISOString()
        }];
        this.messageDraft = '';
        this.sendingMessage = false;
      },
      error: (err) => {
        console.error(err);
        this.sendingMessage = false;
        this.toast.error(err?.error?.message || 'Envoi impossible');
      }
    });
  }

  submitAnnouncement() {
    if (this.announcementForm.invalid) { return; }
    this.isSubmittingAnnouncement = true;
    const sport = this.staff?.sportType as string | undefined;
    const category = (this.staff?.assignedCategory || this.staff?.category) as string | undefined;

    // 'club' → sans ciblage ; 'category' → sport + catégorie du staff.
    // Le serveur revalide le rôle ; le filtrage à la lecture est serveur.
    const publish$ = this.announcementForm.value.scope === 'club' || !sport
      ? this.api.publishAnnouncement({ title: this.announcementForm.value.title, body: this.announcementForm.value.body })
      : this.api.publishAnnouncement(
          { title: this.announcementForm.value.title, body: this.announcementForm.value.body },
          sport, category);

    publish$.subscribe({
      next: () => {
        this.toast.success('Annonce publiée');
        this.isSubmittingAnnouncement = false;
        this.showAnnouncementForm = false;
        this.announcementForm.reset({ title: '', body: '', scope: 'category' });
      },
      error: (err) => {
        console.error(err);
        this.isSubmittingAnnouncement = false;
        this.toast.error(err?.error?.message || 'Publication impossible');
      }
    });
  }
}
