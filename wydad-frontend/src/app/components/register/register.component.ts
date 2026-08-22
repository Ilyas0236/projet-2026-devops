import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html'
})
export class RegisterComponent {
  email = '';
  phone = '';
  password = '';
  firstName = '';
  lastName = '';
  membershipLevel = 'ROUGE';
  referralCode = '';
  loading = false;
  error = '';
  success = false;

  authService = inject(AuthService);
  router = inject(Router);
  route = inject(ActivatedRoute);

  private static readonly ALLOWED_LEVELS = ['JUNIOR', 'ROUGE', 'OR', 'DIAMANT'];

  constructor() {
    // Pre-selection du niveau depuis la page adhesion (?level=OR)
    const level = this.route.snapshot.queryParamMap.get('level');
    if (level && RegisterComponent.ALLOWED_LEVELS.includes(level)) {
      this.membershipLevel = level;
    }
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

    const requestData = {
      email: this.email,
      phone: this.phone,
      password: this.password,
      firstName: this.firstName,
      lastName: this.lastName,
      membershipLevel: this.membershipLevel,
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
