import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
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
  /** Niveau pré-sélectionné depuis la page adhésion — informatif uniquement :
   * le serveur attribue ROUGE à l'inscription ; la montée de niveau se fait
   * après paiement (POST /upgrade). */
  selectedTier: any = null;
  loading = false;
  error = '';
  success = false;
  tiers: any[] = [];

  // ----- Choix du statut -----
  statut: StatutChoix = 'ADHERENT';
  readonly options: StatutOption[] = [
    { valeur: 'ADHERENT',    titre: 'Supporter / Adhérent', description: 'Accès immédiat à l\'espace fan, sondages et billetterie.', icone: '❤' },
    { valeur: 'JOURNALISTE', titre: 'Journaliste',          description: 'Demande d\'accréditation presse — validée par le club.', icone: '📰' },
    { valeur: 'JOUEUR',      titre: 'Joueur',               description: 'Intégration à une catégorie — validée par le club.', icone: '⚽' },
    { valeur: 'ENTRAINEUR',  titre: 'Entraîneur',           description: 'Encadrement d\'une catégorie — validé par le club.', icone: '📋' },
    { valeur: 'STAFF',       titre: 'Staff technique',      description: 'Personnel médical, physique ou manager — validé par le club.', icone: '🩺' }
  ];

  /** Catégories sportives (alignées sur le backend CATEGORIES_VALIDES). */
  readonly categories = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];
  categorieDemandee = '';
  organismePresse = '';
  matchSouhaite = '';

  authService = inject(AuthService);
  api = inject(ApiService);
  router = inject(Router);
  route = inject(ActivatedRoute);

  private static readonly ALLOWED_LEVELS = ['JUNIOR', 'ROUGE', 'OR', 'DIAMANT', 'LEGENDE'];

  get estSportif(): boolean {
    return this.statut === 'JOUEUR' || this.statut === 'ENTRAINEUR' || this.statut === 'STAFF';
  }

  ngOnInit() {
    // Paliers depuis la configuration club (source de verite ADMIN)
    this.api.getClubSetting('membership_tiers').subscribe({
      next: (tiers) => {
        this.tiers = Array.isArray(tiers) ? tiers.filter(t => t.price != null) : [];
        const level = this.route.snapshot.queryParamMap.get('level');
        if (level && RegisterComponent.ALLOWED_LEVELS.includes(level)) {
          this.selectedTier = this.tiers.find(t => t.level === level) || null;
        }
      },
      error: () => {
        this.tiers = [];
      }
    });
  }

  choisirStatut(valeur: StatutChoix) {
    this.statut = valeur;
    // Réinitialisation des champs conditionnels au changement de statut.
    this.categorieDemandee = '';
    this.organismePresse = '';
    this.matchSouhaite = '';
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
    if (this.estSportif && !this.categorieDemandee) return false;
    if (this.statut === 'JOURNALISTE' && this.organismePresse.trim().length === 0) return false;
    return true;
  }

  register() {
    this.loading = true;
    this.error = '';
    this.success = false;

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
      if (this.estSportif) requestData.categorieDemandee = this.categorieDemandee;
      if (this.statut === 'JOURNALISTE') {
        requestData.organismePresse = this.organismePresse.trim();
        requestData.matchSouhaite = this.matchSouhaite.trim() || null;
      }
    }

    this.authService.register(requestData).subscribe({
      next: () => {
        this.success = true;
        this.loading = false;
        setTimeout(() => this.router.navigate(['/login']), 2500);
      },
      error: (err) => {
        this.error = err.error?.message || "Erreur lors de la création du compte. Veuillez vérifier vos informations.";
        this.loading = false;
      }
    });
  }
}
