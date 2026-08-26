import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  error = '';
  token = '';

  constructor(private authService: AuthService, private router: Router) {}

  login() {
    this.loading = true;
    this.error = '';
    this.authService.login(this.email, this.password).subscribe({
      next: () => {
        this.loading = false;
        // Redirection selon le rôle (valeurs de l'enum backend Role.java)
        switch (this.authService.getRole()) {
          case 'ADMIN':
            this.router.navigate(['/admin']);
            break;
          case 'JOUEUR':
            this.router.navigate(['/joueur/dashboard']);
            break;
          case 'STAFF':
            this.router.navigate(['/staff/dashboard']);
            break;
          case 'ENTRAINEUR':
            this.router.navigate(['/entraineur/dashboard']);
            break;
          case 'JOURNALISTE':
            this.router.navigate(['/journaliste/accueil']);
            break;
          case 'PRESIDENT':
            this.router.navigate(['/president/dashboard']);
            break;
          case 'PARENT':
            this.router.navigate(['/academie/mes-enfants']);
            break;
          default:
            this.router.navigate(['/profil/carte']);
        }
      },
      error: (err) => {
        this.error = err.error?.message || 'Erreur de connexion';
        this.loading = false;
      }
    });
  }
}
