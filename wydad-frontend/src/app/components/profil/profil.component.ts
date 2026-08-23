import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../services/confirm.service';

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
  otpMsg = '';
  otpErr = '';

  // Sessions Tab
  sessions: any[] = [];
  sessionMsg = '';

  authService = inject(AuthService);
  router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadProfile();
    this.loadSessions();
  }

  loadProfile() {
    const token = localStorage.getItem('wydad_token');
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }

    this.authService.getProfile().subscribe({
      next: (res: any) => {
        this.profile = res;
        this.editFirstName = res.firstName || '';
        this.editLastName = res.lastName || '';
        this.editPhone = res.phone || '';
        this.editVille = res.ville || '';
        this.editLangue = res.langue || '';
        this.editBio = res.bio || '';
      },
      error: (err) => {
        console.error('Erreur chargement profil', err);
        // Fallback sur localStorage si l'API échoue (session expirée etc.)
        const email = localStorage.getItem('wydad_email');
        if (!email) {
          this.router.navigate(['/login']);
          return;
        }
        this.profile = {
          firstName: localStorage.getItem('wydad_first_name'),
          lastName: localStorage.getItem('wydad_last_name'),
          email: email,
          role: localStorage.getItem('wydad_role')
        };
        this.editFirstName = this.profile.firstName || '';
        this.editLastName = this.profile.lastName || '';
      }
    });
  }

  updateProfile() {
    this.saving = true;
    this.infoMsg = '';
    this.infoErr = '';

    this.authService.updateProfile({
      email: this.profile?.email,
      firstName: this.editFirstName,
      lastName: this.editLastName,
      phone: this.editPhone,
      ville: this.editVille,
      langue: this.editLangue,
      bio: this.editBio
    }).subscribe({
      next: () => {
        this.saving = false;
        if (this.profile) {
          this.profile.firstName = this.editFirstName;
          this.profile.lastName = this.editLastName;
          this.profile.phone = this.editPhone;
        }
        localStorage.setItem('wydad_first_name', this.editFirstName);
        localStorage.setItem('wydad_last_name', this.editLastName);
        this.infoMsg = 'Profil mis à jour avec succès.';
        this.loadProfile();
      },
      error: (err) => {
        this.saving = false;
        this.infoErr = err.error?.message || 'Erreur lors de la mise à jour.';
      }
    });
  }

  async deleteAccount() {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le compte',
      message: 'Êtes-vous sûr de vouloir supprimer votre compte définitivement ? Cette action est irréversible.',
      confirmLabel: 'Supprimer définitivement',
      danger: true
    });
    if (!ok) return;
    this.authService.deleteAccount().subscribe({
      next: () => {
        this.toast.success('Compte supprimé avec succès.');
        this.authService.logout();
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Erreur lors de la suppression du compte.');
      }
    });
  }

  // --- KYC Logic ---
  uploadKyc() {
    this.kycUploading = true;
    this.kycErr = '';
    this.kycMsg = '';
    const email = localStorage.getItem('wydad_email');
    if (!email) { this.kycErr = 'Non connecté.'; this.kycUploading = false; return; }

    this.authService.uploadKyc(this.kycDocType, this.kycDocNumber, this.kycFilePath).subscribe({
      next: () => {
        this.kycUploading = false;
        this.kycUploaded = true;
        this.kycMsg = 'Document uploadé. En attente de validation administrateur.';
      },
      error: (err) => {
        this.kycUploading = false;
        this.kycErr = err.error?.message || 'Erreur lors de l\'upload du document.';
      }
    });
  }

  verifyKyc() {
    this.kycVerifying = true;
    const email = localStorage.getItem('wydad_email');
    if (!email) { this.kycVerifying = false; return; }

    this.authService.verifyKycMock(email).subscribe({
      next: () => {
        this.kycVerifying = false;
        if (this.profile) this.profile.kycVerified = true;
        this.kycMsg = 'KYC validé ! Votre identité est confirmée.';
      },
      error: (err) => {
        this.kycVerifying = false;
        this.kycErr = err.error?.message || 'Erreur lors de la validation KYC.';
      }
    });
  }

  // --- OTP Logic ---
  sendOtp() {
    this.otpSending = true;
    this.otpMsg = '';
    this.otpErr = '';

    this.authService.sendOtp(this.otpPhone).subscribe({
      next: (res: any) => {
        this.otpSending = false;
        this.otpSent = true;
        // La réponse ne contient plus jamais le code : en démo, on le
        // récupère via le canal mock isolé (404 silencieux en production).
        this.otpMsg = 'Code envoyé par SMS.';
        this.authService.getMockOtpCode().subscribe({
          next: (code: any) => {
            if (typeof code === 'string' && /^\d{6}$/.test(code)) {
              this.otpMsg = `Code envoyé. Mode démo — votre code : ${code}`;
            }
          },
          error: () => { /* canal démo désactivé : on garde le message générique */ }
        });
      },
      error: (err) => {
        this.otpSending = false;
        this.otpErr = err.error?.message || 'Erreur lors de l\'envoi du code.';
      }
    });
  }

  verifyOtp() {
    this.otpVerifying = true;
    this.otpMsg = '';
    this.otpErr = '';

    this.authService.verifyOtp(this.otpCode).subscribe({
      next: () => {
        this.otpVerifying = false;
        this.otpMsg = 'Numéro de téléphone vérifié avec succès.';
        this.otpSent = false;
        this.otpCode = '';
      },
      error: (err) => {
        this.otpVerifying = false;
        this.otpErr = err.error?.message || 'Code OTP incorrect.';
      }
    });
  }

  // --- Sessions Logic ---
  loadSessions() {
    this.authService.getSessions().subscribe({
      next: (res: any[]) => {
        this.sessions = (res || []).map((s: any) => ({
          id: s.id,
          device: s.userAgent || 'Appareil inconnu',
          ipAddress: s.ipAddress,
          lastActiveAt: s.createdAt,
          isCurrent: s.currentSession
        }));
      },
      error: (err) => console.error('Erreur chargement sessions', err)
    });
  }

  revokeSession(id: number) {
    this.authService.revokeSession(String(id)).subscribe({
      next: () => {
        this.sessions = this.sessions.filter(s => s.id !== id);
        this.sessionMsg = 'Session révoquée avec succès.';
      },
      error: (err) => {
        this.sessionMsg = err.error?.message || 'Erreur lors de la révocation.';
      }
    });
  }

  async revokeAllSessions() {
    const ok = await this.confirm.confirm({
      title: 'Déconnecter les appareils',
      message: 'Déconnecter tous les autres appareils ?',
      confirmLabel: 'Déconnecter tout'
    });
    if (!ok) return;
    this.authService.revokeAllSessions().subscribe({
      next: () => {
        this.sessions = this.sessions.filter(s => s.isCurrent);
        this.sessionMsg = 'Toutes les autres sessions ont été révoquées.';
      },
      error: (err) => {
        this.sessionMsg = err.error?.message || 'Erreur lors de la révocation.';
      }
    });
  }
}
