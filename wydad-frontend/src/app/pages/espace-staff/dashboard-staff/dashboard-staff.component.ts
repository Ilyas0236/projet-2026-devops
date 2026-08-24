import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-dashboard-staff',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule],
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
          this.staffNotFound = true;
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
