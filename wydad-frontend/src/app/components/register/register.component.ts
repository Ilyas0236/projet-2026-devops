import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';

type StatutChoix = 'ADHERENT' | 'JOURNALISTE' | 'JOUEUR' | 'ENTRAINEUR' | 'STAFF';

interface StatutOption {
  valeur: StatutChoix;
  titre: string;
  description: string;
  icone: string;
}

/**
 * Inscription multi-statuts : l'utilisateur choisit son statut (adhérent,
 * journaliste, joueur, entraîneur, staff). Les statuts privilégiés créent un
 * compte EN_ATTENTE validé par l'ADMIN — catégorie sportive obligatoire pour
 * les rôles sportifs, organe de presse + match souhaité pour les journalistes.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html'
})
export class RegisterComponent implements OnInit {
  email = '';
  phone = '';
  password = '';
  firstName = '';
  lastName = '';
  referralCode = '';
  loading = false;
  error = '';
  success = false;
  /** Vrai quand l'inscription crée un compte EN_ATTENTE (statut privilégié) :
   * pas de session, message dédié "en attente de validation". */
  enAttente = false;

  // ----- Choix du statut -----
  statut: StatutChoix = 'ADHERENT';
  readonly options: StatutOption[] = [
    { valeur: 'ADHERENT',    titre: 'Supporter / Adhérent', description: 'Accès immédiat à l\'espace fan, sondages et billetterie.', icone: '❤' },
    { valeur: 'JOURNALISTE', titre: 'Journaliste',          description: 'Demande d\'accréditation presse — validée par le club.', icone: '📰' },
    { valeur: 'JOUEUR',      titre: 'Joueur',               description: 'Intégration à une catégorie — validée par le club.', icone: '⚽' },
    { valeur: 'ENTRAINEUR',  titre: 'Entraîneur',           description: 'Encadrement d\'une catégorie — validé par le club.', icone: '📋' },
    { valeur: 'STAFF',       titre: 'Staff technique',      description: 'Personnel médical, physique ou manager — validé par le club.', icone: '🩺' }
  ];

  /** Disciplines sportives (alignées sur le backend DISCIPLINES_VALIDES). */
  readonly disciplines = [
    { valeur: 'FOOTBALL', label: 'Football' },
    { valeur: 'BASKETBALL', label: 'Basketball' },
    { valeur: 'HANDBALL', label: 'Handball' },
    { valeur: 'VOLLEYBALL', label: 'Volleyball' },
    { valeur: 'SWIMMING', label: 'Natation' },
    { valeur: 'JUDO', label: 'Judo' },
    { valeur: 'ATHLETICS', label: 'Athlétisme' },
    { valeur: 'AUTRE', label: 'Autre discipline' }
  ];
  /** Catégories sportives (alignées sur le backend CATEGORIES_VALIDES). */
  readonly categories = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];
  disciplineDemandee = '';
  categorieDemandee = '';
  organismePresse = '';
  /** §17 : l'accréditation presse vise un match RÉEL du calendrier
   * (id vérifié par le serveur auprès du content-service). */
  matchId: number | null = null;
  matchsDisponibles: any[] = [];
  matchsLoading = false;

  // ----- Justificatif d'identité (KYC) -----
  /** Pièce d'identité exigée pour les statuts validés par le club :
   * l'admin doit pouvoir vérifier l'identité avant d'approuver. */
  kycFile: File | null = null;
  kycFileName = '';
  kycDocNumber = '';
  kycUploading = false;

  onKycFileSelected(event: any) {
    const input = event.target as HTMLInputElement;
    this.kycFile = input.files && input.files.length ? input.files[0] : null;
    this.kycFileName = this.kycFile?.name || '';
  }

  authService = inject(AuthService);
  api = inject(ApiService);
  router = inject(Router);

  get estSportif(): boolean {
    return this.statut === 'JOUEUR' || this.statut === 'ENTRAINEUR' || this.statut === 'STAFF';
  }

  ngOnInit() {
    // L'inscription crée un compte sans palier pré-sélectionné.
    // Les abonnements sont gérés par l'admin et s'achètent depuis l'espace
    // personnel — voir la page /abonnement.
  }

  choisirStatut(valeur: StatutChoix) {
    this.statut = valeur;
    // Réinitialisation des champs conditionnels au changement de statut.
    this.disciplineDemandee = '';
    this.categorieDemandee = '';
    this.organismePresse = '';
    this.matchId = null;
    // La pièce d'identité est exigée pour les statuts validés par le club.
    this.kycFile = null;
    this.kycFileName = '';
    this.kycDocNumber = '';
    if (valeur === 'JOURNALISTE') {
      this.chargerMatchs();
    }
  }

  /** §17 : le journaliste choisit parmi les matchs RÉELS du calendrier. */
  chargerMatchs() {
    if (this.matchsDisponibles.length || this.matchsLoading) return;
    this.matchsLoading = true;
    this.api.getMatches().subscribe({
      next: (list) => {
        this.matchsDisponibles = list;
        this.matchsLoading = false;
      },
      error: () => {
        this.matchsDisponibles = [];
        this.matchsLoading = false;
      }
    });
  }

  /** Libellé lisible d'un match du calendrier. */
  matchLabel(m: any): string {
    const date = m.date ? new Date(m.date).toLocaleDateString('fr-FR') : '';
    return `Wydad vs ${m.adversaire}${m.competition ? ' — ' + m.competition : ''}${date ? ', le ' + date : ''}`;
  }

  isValidForm(): boolean {
    if (!(
      this.email.includes('@') &&
      this.phone.trim().length > 0 &&
      this.password.length >= 6 &&
      this.firstName.trim().length > 0 &&
      this.lastName.trim().length > 0
    )) {
      return false;
    }
    if (this.estSportif && (!this.disciplineDemandee || !this.categorieDemandee)) return false;
    if (this.statut === 'JOURNALISTE' && this.organismePresse.trim().length === 0) return false;
    // §17 : un match réel du calendrier est obligatoire pour la presse.
    if (this.statut === 'JOURNALISTE' && !this.matchId) return false;
    // Pièce d'identité exigée pour les statuts soumis à validation du club.
    if (this.statut !== 'ADHERENT' && (!this.kycFile || this.kycDocNumber.trim().length === 0)) return false;
    return true;
  }

  register() {
    this.loading = true;
    this.error = '';
    this.success = false;
    this.enAttente = false;

    // Pas de membershipLevel dans la requête : le serveur force le niveau de
    // départ — la montée passe par /upgrade après paiement.
    const requestData: any = {
      email: this.email,
      phone: this.phone,
      password: this.password,
      firstName: this.firstName,
      lastName: this.lastName,
      referralCode: this.referralCode ? this.referralCode : null
    };

    // Demande de statut : envoyée seulement si différente d'adhérent.
    if (this.statut !== 'ADHERENT') {
      requestData.demandeRole = this.statut;
      if (this.estSportif) {
        requestData.disciplineDemandee = this.disciplineDemandee;
        requestData.categorieDemandee = this.categorieDemandee;
      }
      if (this.statut === 'JOURNALISTE') {
        requestData.organismePresse = this.organismePresse.trim();
        // §17 : id du match réel choisi — le serveur vérifie son existence.
        requestData.matchId = this.matchId;
      }
    }

    this.authService.register(requestData).subscribe({
      next: (res) => {
        if (res && res.accessToken) {
          // Adhérent : compte VALIDE, session ouverte immédiatement.
          this.loading = false;
          this.success = true;
          setTimeout(() => this.router.navigate(['/login']), 2500);
        } else {
          // Statut privilégié : 202 sans corps — compte EN_ATTENTE.
          // On dépose ensuite le justificatif d'identité (auth par
          // email+password, pas de session nécessaire).
          this.deposerKyc();
        }
      },
      error: (err) => {
        this.error = err.error?.message || "Erreur lors de la création du compte. Veuillez vérifier vos informations.";
        this.loading = false;
      }
    });
  }

  /** Dépôt du document d'identité après la création du compte EN_ATTENTE.
   * En cas d'échec d'upload, le compte existe quand même : on l'indique
   * clairement plutôt que de perdre l'inscription. */
  private deposerKyc() {
    if (!this.kycFile || this.statut === 'ADHERENT') {
      this.loading = false;
      this.enAttente = true;
      return;
    }
    this.kycUploading = true;
    this.authService.uploadKycRegister(this.kycFile, 'CIN', this.kycDocNumber.trim(), this.email.trim(), this.password)
      .subscribe({
        next: () => {
          this.kycUploading = false;
          this.loading = false;
          this.enAttente = true;
        },
        error: () => {
          this.kycUploading = false;
          this.loading = false;
          this.enAttente = true;
          this.error = "Compte créé, mais le dépôt du document a échoué. Vous pourrez le renvoyer depuis votre page de connexion auprès du club.";
        }
      });
  }
}
