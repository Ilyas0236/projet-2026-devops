import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <h2>🔴⚫ Connexion Wydad AC</h2>
        <div class="form-group">
          <label>Email</label>
          <input type="email" [(ngModel)]="email" placeholder="donateur@wydad.ma">
        </div>
        <div class="form-group">
          <label>Mot de passe</label>
          <input type="password" [(ngModel)]="password" placeholder="password123">
        </div>
        <button (click)="login()" [disabled]="loading">
          {{ loading ? 'Connexion...' : 'Se connecter' }}
        </button>
        <div *ngIf="error" class="error">{{ error }}</div>
        <div *ngIf="token" class="success">
          ✅ Connecté ! Token stocké.
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 60vh;
    }
    .login-card {
      background: white;
      padding: 2.5rem;
      border-radius: 12px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.15);
      width: 100%;
      max-width: 400px;
      border-top: 4px solid #d32f2f;
    }
    h2 {
      text-align: center;
      color: #b71c1c;
      margin-bottom: 1.5rem;
    }
    .form-group {
      margin-bottom: 1rem;
    }
    label {
      display: block;
      margin-bottom: 0.5rem;
      color: #333;
      font-weight: 500;
    }
    input {
      width: 100%;
      padding: 0.75rem;
      border: 2px solid #ddd;
      border-radius: 6px;
      font-size: 1rem;
      transition: border-color 0.3s;
    }
    input:focus {
      outline: none;
      border-color: #d32f2f;
    }
    button {
      width: 100%;
      padding: 0.875rem;
      background: linear-gradient(90deg, #d32f2f, #b71c1c);
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 1rem;
      font-weight: bold;
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    button:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(211, 47, 47, 0.4);
    }
    button:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
    .error {
      margin-top: 1rem;
      padding: 0.75rem;
      background: #ffebee;
      color: #c62828;
      border-radius: 6px;
      text-align: center;
    }
    .success {
      margin-top: 1rem;
      padding: 0.75rem;
      background: #e8f5e9;
      color: #2e7d32;
      border-radius: 6px;
      text-align: center;
    }
  `]
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  error = '';
  token = '';

  constructor(private api: ApiService, private router: Router) {}

  login() {
    this.loading = true;
    this.error = '';
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        this.token = res.accessToken;
        localStorage.setItem('wydad_token', res.accessToken);
        localStorage.setItem('wydad_email', res.email);
        this.loading = false;
        setTimeout(() => this.router.navigate(['/carte']), 1000);
      },
      error: (err) => {
        this.error = err.error?.message || 'Erreur de connexion';
        this.loading = false;
      }
    });
  }
}