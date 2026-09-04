import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';
import { ErrorBannerComponent } from '../../../components/error-banner/error-banner.component';
import { TeamChatComponent } from '../../../components/team-chat/team-chat.component';
import { ScheduleCallFormComponent } from '../../../components/my-calls/schedule-call-form.component';
import { MessagerieLibreComponent } from '../../../components/messagerie-libre/messagerie-libre.component';

/**
 * Espace Président — page « Discussions » (Chantier 1 du plan qualité).
 *
 * Le président du club a ici une vue transversale par DISCIPLINE puis
 * CATÉGORIE. Pour chaque couple (discipline, catégorie), il accède à :
 *   - la liste des membres (joueurs + entraîneur principal HEAD_COACH) ;
 *   - le chat de groupe WhatsApp-style (réutilise TeamChatComponent) ;
 *   - le démarrage d'un appel vocal/vidéo LiveKit (Phase 5) avec toute
 *     l'équipe (réutilise ScheduleCallFormComponent).
 *
 * Côté backend, le contrôleur TeamChatController autorise désormais le
 * rôle PRESIDENT sur tous les groupes (sans fiche roster). L'appel
 * passe par sports-service /api/sports/calls qui supporte déjà
 * PRESIDENT (cf. ScheduledCallService.createCall).
 *
 * Le coach principal (HEAD_COACH de la catégorie) est TOUJOURS inclus
 * dans le groupe/appel (cf. exigence métier : « avant un match avec
 * leur entraîneur »).
 */
@Component({
  selector: 'app-president-discussions',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorBannerComponent, TeamChatComponent, ScheduleCallFormComponent, MessagerieLibreComponent],
  templateUrl: './president-discussions.component.html',
  styleUrls: ['./president-discussions.component.scss']
})
export class PresidentDiscussionsComponent implements OnInit {
  // Disciplines alignées sur SportType (sports-service) + AUTRE.
  // Aligner ici évite tout couplage caché : la toolbar reflète strictement
  // les groupes WhatsApp existants.
  readonly disciplines = [
    { code: 'FOOTBALL',   label: 'Football' },
    { code: 'BASKETBALL', label: 'Basketball' },
    { code: 'HANDBALL',   label: 'Handball' },
    { code: 'VOLLEYBALL', label: 'Volleyball' },
    { code: 'SWIMMING',   label: 'Natation' },
    { code: 'JUDO',       label: 'Judo' },
    { code: 'ATHLETICS',  label: 'Athlétisme' },
    { code: 'AUTRE',      label: 'Autre' }
  ];

  // Catégories alignées sur Category (sports-service) — U15..SENIOR.
  readonly categories = [
    { code: 'U15',    label: 'U15' },
    { code: 'U17',    label: 'U17' },
    { code: 'U18',    label: 'U18' },
    { code: 'U20',    label: 'U20' },
    { code: 'SENIOR', label: 'Senior' }
  ];

  selectedDiscipline: string | null = null;
  selectedCategory: string | null = null;
  members: Array<{ userId: number; fullName: string; rosterRole: string }> = [];
  membersLoading = false;
  callOpen = false;
  loadError = false;
  loadErrorMessage = '';

  /** V2.5 — messagerie libre (sélecteur 1..N) ouverte en modale depuis le bouton 1-1. */
  messagerieLibreOpen = false;
  /** V2.5 — userId du destinataire pré-coché dans la messagerie libre. */
  prefillLibreToUserId: number | null = null;

  api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  ngOnInit(): void {
    // Pas d'auto-sélection : le président choisit explicitement sa cible.
  }

  /** Bouton « Réessayer » du bandeau d'erreur. */
  retryLoad(): void {
    this.loadError = false;
    if (this.selectedDiscipline && this.selectedCategory) {
      this.loadMembers();
    }
  }

  selectDiscipline(code: string): void {
    this.selectedDiscipline = code;
    this.selectedCategory = null;
    this.members = [];
  }

  selectCategory(code: string): void {
    if (!this.selectedDiscipline) return;
    this.selectedCategory = code;
    this.loadMembers();
  }

  /**
   * Charge la liste des membres du groupe (joueurs + staff encadrant).
   * L'entraîneur principal HEAD_COACH est inclus automatiquement (côté
   * sports-service, l'audience d'un appel/groupe le contient par défaut).
   */
  loadMembers(): void {
    if (!this.selectedDiscipline || !this.selectedCategory) return;
    this.membersLoading = true;
    const sport = this.selectedDiscipline;
    const cat = this.selectedCategory;
    this.api.getTeamChatMembers(sport, cat).subscribe({
      next: (data) => {
        this.members = data || [];
        this.membersLoading = false;
      },
      error: () => {
        this.membersLoading = false;
        this.toast.error('Impossible de charger les membres de cette équipe');
      }
    });
  }

  /**
   * Ouvre un chat 1-1 avec un membre via le sélecteur libre V2.5
   * pré-rempli avec cet interlocuteur. Le président peut décocher
   * et ajouter d'autres membres de la même équipe.
   */
  openOneToOne(member: { userId: number; fullName: string }): void {
    this.prefillLibreToUserId = member.userId;
    this.messagerieLibreOpen = true;
  }

  /** V2.5 — fermeture du sélecteur libre. */
  closeMessagerieLibre(): void {
    this.messagerieLibreOpen = false;
    this.prefillLibreToUserId = null;
  }

  /** Ouvre le formulaire d'appel LiveKit (Phase 5) — audience = tous les membres. */
  openCallDialog(): void {
    if (!this.selectedDiscipline || !this.selectedCategory) return;
    if (this.members.length === 0) {
      this.toast.error('Aucun membre dans cette équipe : appel impossible');
      return;
    }
    this.callOpen = true;
  }

  /** Appelé quand l'appel est créé — ferme la modale. */
  onCallCreated(): void {
    this.callOpen = false;
    this.toast.success('Appel programmé — les membres sont notifiés');
  }

  /** Appelé si l'utilisateur ferme le formulaire. */
  onCallCancelled(): void {
    this.callOpen = false;
  }

  /** Audience calculée pour l'appel = membres + coach inclus. */
  callAudience(): number[] {
    return this.members.map((m) => m.userId);
  }

  hasCoach(): boolean {
    return this.members.some((m) => m.rosterRole === 'STAFF');
  }
}
