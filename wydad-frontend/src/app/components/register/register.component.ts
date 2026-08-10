import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="register-container">
      <div class="register-card">
        <h2>🔴⚫ Inscription Supporter WAC</h2>
        
        <div class="form-row">
          <div class="form-group">
            <label>Prénom</label>
            <input type="text" [(ngModel)]="firstName" placeholder="Prénom">
          </div>
          <div class="form-group">
            <label>Nom</label>
            <input type="text" [(ngModel)]="lastName" placeholder="Nom">
          </div>
        </div>

        <div class="form-group">
          <label>Email</label>
          <input type="email" [(ngModel)]="email" placeholder="exemple@wydad.ma">
        </div>

        <div class="form-group">
          <label>Téléphone</label>
          <input type="text" [(ngModel)]="phone" placeholder="+212 600-000000">
        </div>

        <div class="form-group">
          <label>Mot de passe</label>
          <input type="password" [(ngModel)]="password" placeholder="Minimum 6 caractères">
        </div>

        <div class="form-group">
          <label>Niveau d'Adhésion</label>
          <select [(ngModel)]="membershipLevel">
            <option value="ROUGE">Rouge (500 DH/an)</option>
            <option value="OR">Or (1 200 DH/an)</option>
            <option value="DIAMANT">Diamant (3 000 DH/an)</option>
            <option value="JUNIOR">Junior (200 DH/an)</option>
          </select>
        </div>

        <div class="form-group">
          <label>Code de parrainage (Optionnel)</label>
          <input type="text" [(ngModel)]="referralCode" placeholder="Ex: WAC-REF-123">
        </div>

        <button (click)="register()" [disabled]="loading || !isValidForm()">
          {{ loading ? 'Création du compte...' : 'Créer mon compte' }}
        </button>

        <div *ngIf="error" class="error">{{ error }}</div>
        <div *ngIf="success" class="success">
          ✅ Compte créé avec succès ! Redirection vers la page de connexion...
        </div>

        <div class="login-link">
          Déjà un compte ? <a routerLink="/login">Se connecter</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .register-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 2rem 1rem;
      background: #f8f9fa;
    }
    .register-card {
      background: white;
      padding: 2.5rem;
      border-radius: 16px;
      box-shadow: 0 4px 25px rgba(0,0,0,0.1);
      width: 100%;
      max-width: 500px;
      border-top: 5px solid #d32f2f;
    }
    h2 {
      text-align: center;
      color: #b71c1c;
      margin-bottom: 2rem;
      font-size: 1.6rem;
    }
    .form-row {
      display: flex;
      gap: 1rem;
    }
    .form-row .form-group {
      flex: 1;
    }
    .form-group {
      margin-bottom: 1.25rem;
    }
    label {
      display: block;
      margin-bottom: 0.5rem;
      color: #333;
      font-weight: 500;
      font-size: 0.9rem;
    }
    input, select {
      width: 100%;
      padding: 0.75rem;
      border: 2px solid #ddd;
      border-radius: 8px;
      font-size: 1rem;
      transition: border-color 0.3s;
      box-sizing: border-box;
    }
    input:focus, select:focus {
      outline: none;
      border-color: #d32f2f;
    }
    button {
      width: 100%;
      padding: 0.9rem;
      background: linear-gradient(90deg, #d32f2f, #b71c1c);
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 1.05rem;
      font-weight: bold;
      cursor: pointer;
      margin-top: 1rem;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    button:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(211, 47, 47, 0.4);
    }
    button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .error {
      margin-top: 1rem;
      padding: 0.75rem;
      background: #ffebee;
      color: #c62828;
      border-radius: 8px;
      text-align: center;
      font-size: 0.95rem;
    }
    .success {
      margin-top: 1rem;
      padding: 0.75rem;
      background: #e8f5e9;
      color: #2e7d32;
      border-radius: 8px;
      text-align: center;
      font-size: 0.95rem;
    }
    .login-link {
      text-align: center;
      margin-top: 1.5rem;
      color: #666;
      font-size: 0.95rem;
    }
    .login-link a {
      color: #d32f2f;
      font-weight: bold;
      text-decoration: none;
    }
    .login-link a:hover {
      text-decoration: underline;
    }
  `]
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
