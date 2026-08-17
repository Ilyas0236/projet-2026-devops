import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-profil',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './profil.component.html',
  styles: []
})
export class ProfilComponent implements OnInit {
  profile: any = null;
  activeTab = 'info';

  // Info Tab
  editFirstName = '';
  editLastName = '';
  editPhone = '';
  editVille = '';
  editLangue = '';
  editBio = '';
  saving = false;
  infoMsg = '';
  infoErr = '';

  // KYC Tab
  kycDocType = 'CIN';
  kycDocNumber = '';
  kycFilePath = '';
  kycUploading = false;
  kycUploaded = false;
  kycVerifying = false;
  kycMsg = '';
  kycErr = '';

  // OTP Tab
  otpPhone = '';
  otpSending = false;
  otpSent = false;
  otpVerifying = false;
  otpCode = '';
  mockOtpCode = '';
  otpMsg = '';
  otpErr = '';

  // Sessions Tab
  sessions: any[] = [];
  sessionMsg = '';

  authService = inject(AuthService);
  router = inject(Router);

  ngOnInit() {
    this.loadProfile();
    this.loadSessions();
  }

  loadProfile() {
    const email = localStorage.getItem('wydad_email');
    if (!email) {
      this.router.navigate(['/login']);
      return;
    }
    
    // Simulate fetching full profile
    this.profile = {
      firstName: localStorage.getItem('wydad_first_name'),
      lastName: localStorage.getItem('wydad_last_name'),
      email: email,
      role: localStorage.getItem('wydad_role'),
      phone: '+212 600-000000',
      membershipExpiresAt: new Date(new Date().setFullYear(new Date().getFullYear() + 1)),
      createdAt: new Date(),
      kycVerified: false,
      referralCode: 'WAC-' + Math.random().toString(36).substring(2, 8).toUpperCase()
    };

    this.editFirstName = this.profile.firstName || '';
    this.editLastName = this.profile.lastName || '';
    this.editPhone = this.profile.phone || '';
  }

  loadSessions() {
    this.sessions = [
      { id: 1, device: 'Chrome sur Windows', ipAddress: '192.168.1.10', lastActiveAt: new Date(), isCurrent: true },
      { id: 2, device: 'Safari sur iPhone 13', ipAddress: '10.0.0.5', lastActiveAt: new Date(new Date().getTime() - 86400000), isCurrent: false }
    ];
  }

  updateProfile() {
    this.saving = true;
    this.infoMsg = '';
    this.infoErr = '';
    
    setTimeout(() => {
      this.saving = false;
      this.profile.firstName = this.editFirstName;
      this.profile.lastName = this.editLastName;
      this.profile.phone = this.editPhone;
      
      const email = localStorage.getItem('wydad_email');
      if(email) {
        localStorage.setItem('wydad_first_name', this.editFirstName);
        localStorage.setItem('wydad_last_name', this.editLastName);
      }
      this.infoMsg = 'Profil mis à jour avec succès.';
    }, 1000);
  }

  deleteAccount() {
    if(confirm('Êtes-vous sûr de vouloir supprimer votre compte définitivement ?')) {
      this.authService.logout();
    }
  }

  // --- KYC Logic ---
  uploadKyc() {
    this.kycUploading = true;
    this.kycErr = '';
    this.kycMsg = '';
    setTimeout(() => {
      this.kycUploading = false;
      this.kycUploaded = true;
      this.kycMsg = 'Document uploadé. En attente de validation administrateur.';
    }, 1500);
  }

  verifyKyc() {
    this.kycVerifying = true;
    setTimeout(() => {
      this.kycVerifying = false;
      this.profile.kycVerified = true;
      this.kycMsg = 'KYC validé ! Votre identité est confirmée.';
    }, 2000);
  }

  // --- OTP Logic ---
  sendOtp() {
    this.otpSending = true;
    this.otpMsg = '';
    this.otpErr = '';
    setTimeout(() => {
      this.otpSending = false;
      this.otpSent = true;
      this.mockOtpCode = Math.floor(100000 + Math.random() * 900000).toString();
    }, 1000);
  }

  verifyOtp() {
    this.otpVerifying = true;
    this.otpMsg = '';
    this.otpErr = '';
    setTimeout(() => {
      this.otpVerifying = false;
      if (this.otpCode === this.mockOtpCode) {
        this.otpMsg = 'Numéro de téléphone vérifié avec succès.';
        this.otpSent = false;
        this.otpCode = '';
        this.mockOtpCode = '';
      } else {
        this.otpErr = 'Code OTP incorrect.';
      }
    }, 1000);
  }

  // --- Sessions Logic ---
  revokeSession(id: number) {
    this.sessions = this.sessions.filter(s => s.id !== id);
    this.sessionMsg = 'Session révoquée avec succès.';
  }

  revokeAllSessions() {
    if(confirm('Déconnecter tous les autres appareils ?')) {
      this.sessions = this.sessions.filter(s => s.isCurrent);
      this.sessionMsg = 'Toutes les autres sessions ont été révoquées.';
    }
  }
}
