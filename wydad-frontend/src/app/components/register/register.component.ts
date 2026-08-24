import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';

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

  authService = inject(AuthService);
  api = inject(ApiService);
  router = inject(Router);
  route = inject(ActivatedRoute);

  private static readonly ALLOWED_LEVELS = ['JUNIOR', 'ROUGE', 'OR', 'DIAMANT', 'LEGENDE'];

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

  isValidForm(): boolean {
    return (
      this.email.includes('@') &&
      this.phone.trim().length > 0 &&
      this.password.length >= 6 &&
      this.firstName.trim().length > 0 &&
      this.lastName.trim().length > 0
    );
  }

  register() {
    this.loading = true;
    this.error = '';
    this.success = false;

    // Pas de membershipLevel dans la requête : le serveur force le niveau de
    // départ — la montée passe par /upgrade après paiement.
    const requestData = {
      email: this.email,
      phone: this.phone,
      password: this.password,
      firstName: this.firstName,
      lastName: this.lastName,
      referralCode: this.referralCode ? this.referralCode : null
    };

    this.authService.register(requestData).subscribe({
      next: () => {
        this.success = true;
        this.loading = false;
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.error = err.error?.message || "Erreur lors de la création du compte. Veuillez vérifier vos informations.";
        this.loading = false;
      }
    });
  }
}
