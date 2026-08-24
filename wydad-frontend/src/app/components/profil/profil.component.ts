import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
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
  /** Fichier réellement sélectionné (envoyé en multipart vers Cloudinary). */
  private kycFile: File | null = null;
  /** Accès template : un vrai fichier a-t-il été choisi ? */
  get hasKycFile(): boolean { return this.kycFile !== null; }
  kycFileName = '';
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

  // B.10 — Réclamations & support
  reclamations: any[] = [];
  reclamationDraft = { subject: 'SHOP', title: '', description: '' };

  // Fonctionnalité 4/6 — Préférences de notification (appliquées à l'envoi
  // côté serveur, prouvé par NotificationPreferenceTest).
  prefs = { emailEnabled: true, pushEnabled: true, inAppEnabled: true };
  prefsLoading = true;
  prefsSaving = false;

  // Transparence financière — rapports publiés par l'ADMIN
  rapportsFinanciers: any[] = [];
  rapportsLoading = true;

  loadRapportsFinanciers() {
    this.apiService.getRapportsFinanciers().subscribe({
      next: (list) => {
        this.rapportsFinanciers = Array.isArray(list) ? list : [];
        this.rapportsLoading = false;
      },
      error: () => (this.rapportsLoading = false)
    });
  }

  loadPreferences() {
    this.prefsLoading = true;
    this.apiService.getMyPreferences().subscribe({
      next: (p) => {
        this.prefs = {
          emailEnabled: p.emailEnabled !== false,
          pushEnabled: p.pushEnabled !== false,
          inAppEnabled: p.inAppEnabled !== false
        };
        this.prefsLoading = false;
      },
      error: () => {
        this.prefsLoading = false;
      }
    });
  }

  togglePreference(channel: 'emailEnabled' | 'pushEnabled' | 'inAppEnabled') {
    if (this.prefsSaving) return;
    this.prefs[channel] = !this.prefs[channel];
    this.prefsSaving = true;
    this.apiService.updateMyPreferences({ ...this.prefs }).subscribe({
      next: (p) => {
        this.prefs = { ...this.prefs };
        this.prefsSaving = false;
        const label = channel === 'emailEnabled' ? 'E-mail'
          : channel === 'pushEnabled' ? 'Notifications push' : 'Notifications in-app';
        this.toast.success(`${label} : ${this.prefs[channel] ? 'activé' : 'désactivé'}. Le club ne vous contactera plus par ce canal si désactivé.`);
      },
      error: (err) => {
        // Revert on failure
        this.loadPreferences();
        this.toast.error(err.error?.message || 'Erreur lors de la mise à jour des préférences.');
      }
    });
  }

  apiService = inject(ApiService);

  authService = inject(AuthService);
  router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadProfile();
    this.loadSessions();
    this.loadReclamations();
    this.loadPreferences();
    this.loadRapportsFinanciers();
  }

  // --- B.10 : Réclamations ---
  loadReclamations() {
    this.apiService.getMyReclamations().subscribe({
      next: (list) => (this.reclamations = list || []),
      error: () => (this.reclamations = [])
    });
  }

  canSubmitReclamation(): boolean {
    return !!(this.reclamationDraft.title.trim() && this.reclamationDraft.description.trim());
  }

  submitReclamation() {
    if (!this.canSubmitReclamation()) return;
    this.apiService.createReclamation({
      subject: this.reclamationDraft.subject,
      title: this.reclamationDraft.title.trim(),
      description: this.reclamationDraft.description.trim()
    }).subscribe({
      next: () => {
        this.toast.success('Réclamation envoyée. Le club vous répondra ici même.');
        this.reclamationDraft.title = '';
        this.reclamationDraft.description = '';
        this.loadReclamations();
      },
      error: (err) => this.toast.error(err.error?.message || "Échec de l'envoi.")
    });
  }

  /** Libellé affiché du statut serveur. */
  reclamationStatusLabel(status: string): string {
    switch (status) {
      case 'OPEN': return 'Ouverte';
      case 'IN_PROGRESS': return 'En cours';
      case 'RESOLVED': return 'Résolue';
      case 'REJECTED': return 'Rejetée';
      default: return status;
    }
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
  /** Ouvre le sélecteur de fichier natif (input file caché). */
  browseKycFile(fileInput: HTMLInputElement) {
    fileInput.click();
  }

  /** Réagit à la sélection d'un fichier : valide type + taille puis garde le File en mémoire. */
  onKycFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.kycErr = '';
    this.kycMsg = '';
    const file = input.files?.[0];
    if (!file) { this.kycFile = null; this.kycFileName = ''; return; }

    const allowed = ['image/jpeg', 'image/png', 'application/pdf'];
    if (!allowed.includes(file.type)) {
      this.kycErr = 'Format non supporté. Formats acceptés : JPG, PNG ou PDF.';
      input.value = '';
      this.kycFile = null;
      this.kycFileName = '';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.kycErr = 'Fichier trop volumineux (maximum 10 Mo).';
      input.value = '';
      this.kycFile = null;
      this.kycFileName = '';
      return;
    }
    // Phase 1 : le fichier est conservé tel quel et part en multipart vers
    // /kyc/upload-file (stockage Cloudinary côté backend).
    this.kycFile = file;
    this.kycFileName = file.name;
  }

  uploadKyc() {
    this.kycErr = '';
    this.kycMsg = '';

    // Phase 1 — upload RÉEL si un fichier a été choisi ; sinon fallback sur
    // l'ancien flux (référence textuelle) pour ne pas casser le parcours.
    if (this.kycFile) {
      if (!this.kycDocNumber.trim()) { this.kycErr = 'Renseignez le numéro du document.'; return; }
      this.kycUploading = true;
      this.authService.uploadKycFile(this.kycFile, this.kycDocType, this.kycDocNumber.trim()).subscribe({
        next: () => {
          this.kycUploading = false;
          this.kycUploaded = true;
          this.kycMsg = 'Document envoyé. En attente de validation administrateur.';
        },
        error: (err) => {
          this.kycUploading = false;
          const raison = err.status === 401 ? ' (session expirée — reconnectez-vous)'
            : err.status === 413 ? ' (fichier trop volumineux)'
            : err.status === 403 ? ' (action non autorisée pour votre compte)'
            : '';
          this.kycErr = (err.error?.message || 'Échec du téléversement') + raison + ` [erreur ${err.status}]`;
        }
      });
      return;
    }

    const email = localStorage.getItem('wydad_email');
    if (!email) { this.kycErr = 'Non connecté.'; return; }
    if (!this.kycFilePath.trim()) { this.kycErr = 'Choisissez un fichier ou indiquez une référence document.'; return; }

    this.kycUploading = true;
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
