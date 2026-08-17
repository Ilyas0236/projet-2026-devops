import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
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