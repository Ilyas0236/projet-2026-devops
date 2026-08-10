import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-profil',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-header">
      <h1>⚙️ Mon Espace Personnel</h1>
      <p>Gérez vos données personnelles, votre sécurité et vos sessions actives</p>
    </div>

    <div class="container">
      <div class="profile-layout">
        <!-- SIDEBAR INFO -->
        <div class="profile-card sidebar">
          <div class="avatar-section">
            <div class="avatar">🔴</div>
            <h2>{{ profile?.firstName }} {{ profile?.lastName }}</h2>
            <span class="role-badge">{{ profile?.role }}</span>
            <span class="member-badge" [class]="profile?.membershipLevel">{{ profile?.membershipLevel }}</span>
          </div>
          <div class="meta-info">
            <p><strong>📧 Email :</strong> {{ profile?.email }}</p>
            <p><strong>📞 Tel :</strong> {{ profile?.phone || 'Non renseigné' }}</p>
            <p><strong>🎟️ Parrainage :</strong> <code>{{ profile?.referralCode }}</code></p>
            <p><strong>⏳ Expire le :</strong> {{ profile?.membershipExpiresAt | date:'dd/MM/yyyy' }}</p>
            <p><strong>📅 Adhérent depuis :</strong> {{ profile?.createdAt | date:'dd/MM/yyyy' }}</p>
            <p><strong>🛡️ Statut KYC :</strong> 
              <span class="kyc-status" [class.verified]="profile?.kycVerified">
                {{ profile?.kycVerified ? '✅ Vérifié' : '⚠️ Non vérifié' }}
              </span>
            </p>
          </div>
          
          <div class="profile-nav-links mb-6 border-t border-gray-100 pt-4">
            <a routerLink="/profil" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700" [routerLinkActiveOptions]="{exact: true}">⚙️ Paramètres du compte</a>
            <a routerLink="/profil/carte" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700">🎟️ Ma Carte Membre</a>
            <a routerLink="/profil/ecash" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700">💰 Porte-Monnaie E-Cash</a>
            <a routerLink="/profil/billets" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700">🎫 Mes Billets</a>
            <a routerLink="/profil/commandes" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700">📦 Mes Commandes</a>
            <a routerLink="/academie/mes-enfants" class="block w-full text-left py-2 px-3 rounded text-gray-700 hover:bg-gray-100 font-medium mb-1" routerLinkActive="bg-gray-100 text-red-700">⚽ Mes Enfants (Académie)</a>
            <a *ngIf="profile?.role === 'PLAYER'" routerLink="/joueur/dashboard" class="block w-full text-left py-2 px-3 rounded text-white bg-wydad-dark hover:bg-black font-bold mb-1 mt-4 transition-colors">🔥 Mon Espace Joueur</a>
            <a *ngIf="profile?.role === 'STAFF'" routerLink="/staff/dashboard" class="block w-full text-left py-2 px-3 rounded text-white bg-wydad-dark hover:bg-black font-bold mb-1 mt-4 transition-colors">📋 Mon Espace Staff</a>
          </div>

          <button (click)="deleteAccount()" class="btn-danger-outline">Supprimer mon compte</button>
        </div>

        <!-- MAIN FORMS -->
        <div class="main-content">
          <!-- TABS -->
          <div class="tabs">
            <button (click)="activeTab = 'info'" [class.active]="activeTab === 'info'">📝 Informations</button>
            <button (click)="activeTab = 'sessions'" [class.active]="activeTab === 'sessions'">💻 Sessions Actives</button>
            <button (click)="activeTab = 'kyc'" [class.active]="activeTab === 'kyc'">🛡️ KYC & Identité</button>
            <button (click)="activeTab = 'otp'" [class.active]="activeTab === 'otp'">📱 Sécurité 2FA / OTP</button>
          </div>

          <!-- TAB CONTENT: INFO -->
          <div class="tab-content" *ngIf="activeTab === 'info'">
            <h3>Modifier mes informations</h3>
            <div class="form-row">
              <div class="form-group">
                <label>Prénom</label>
                <input type="text" [(ngModel)]="editFirstName">
              </div>
              <div class="form-group">
                <label>Nom</label>
                <input type="text" [(ngModel)]="editLastName">
              </div>
            </div>
            <div class="form-group">
              <label>Téléphone</label>
              <input type="text" [(ngModel)]="editPhone">
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Ville</label>
                <input type="text" [(ngModel)]="editVille" placeholder="Casablanca">
              </div>
              <div class="form-group">
                <label>Langue</label>
                <input type="text" [(ngModel)]="editLangue" placeholder="Français">
              </div>
            </div>
            <div class="form-group">
              <label>Bio</label>
              <textarea [(ngModel)]="editBio" rows="3" placeholder="Parlez-nous de vous, supporter du Wydad..."></textarea>
            </div>
            <button (click)="updateProfile()" [disabled]="saving">
              {{ saving ? 'Enregistrement...' : 'Enregistrer les modifications' }}
            </button>
            <p *ngIf="infoMsg" class="message success">{{ infoMsg }}</p>
            <p *ngIf="infoErr" class="message error">{{ infoErr }}</p>
          </div>

          <!-- TAB CONTENT: SESSIONS -->
          <div class="tab-content" *ngIf="activeTab === 'sessions'">
            <div class="section-header">
              <h3>Sessions actives</h3>
              <button (click)="revokeAllSessions()" class="btn-danger-small">Déconnecter les autres appareils</button>
            </div>
            <p class="description">Liste des appareils qui se sont connectés à votre compte. Vous pouvez révoquer l'accès à tout moment.</p>

            <div class="sessions-list">
              <div class="session-item" *ngFor="let session of sessions" [class.current]="session.isCurrent">
                <div class="session-icon">💻</div>
                <div class="session-details">
                  <div class="session-title">
                    <strong>{{ session.device || 'Appareil inconnu' }}</strong>
                    <span *ngIf="session.isCurrent" class="current-tag">Session actuelle</span>
                  </div>
                  <div class="session-meta">
                    <span>🌐 IP : {{ session.ipAddress }}</span>
                    <span>🕒 Activité : {{ session.lastActiveAt | date:'dd/MM/yyyy HH:mm' }}</span>
                  </div>
                </div>
                <button 
                  *ngIf="!session.isCurrent" 
                  (click)="revokeSession(session.id)" 
                  class="btn-revoke"
                >Révoquer</button>
              </div>
              <div *ngIf="sessions.length === 0" class="empty-list">Aucune session enregistrée.</div>
            </div>
            <p *ngIf="sessionMsg" class="message success">{{ sessionMsg }}</p>
          </div>

          <!-- TAB CONTENT: KYC -->
          <div class="tab-content" *ngIf="activeTab === 'kyc'">
            <h3>Vérification d'Identité (KYC)</h3>
            <p class="description">Pour accéder à l'intégralité des services du Wydad E-Cash et sécuriser votre compte supporter, vous devez fournir une pièce d'identité valide.</p>
            
            <div class="kyc-panel" *ngIf="profile?.kycVerified; else notVerifiedKyc">
              <div class="kyc-badge verified">
                <span class="icon">✅</span>
                <div>
                  <h4>Votre compte est vérifié</h4>
                  <p>Vos documents d'identité ont été approuvés par nos administrateurs.</p>
                </div>
              </div>
            </div>

            <ng-template #notVerifiedKyc>
              <div class="kyc-form">
                <div class="form-group">
                  <label>Type de document</label>
                  <select [(ngModel)]="kycDocType">
                    <option value="CIN">Carte d'Identité Nationale (CIN)</option>
                    <option value="PASSPORT">Passeport</option>
                    <option value="PERMIS">Permis de conduire</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Numéro de document</label>
                  <input type="text" [(ngModel)]="kycDocNumber" placeholder="Ex: BE123456">
                </div>
                <div class="form-group">
                  <label>Chemin du document d'identité (Faux fichier pour démo)</label>
                  <input type="text" [(ngModel)]="kycFilePath" placeholder="Ex: C:/Users/Supporter/Documents/cin.jpg">
                </div>
                
                <div class="actions-kyc">
                  <button (click)="uploadKyc()" [disabled]="kycUploading || !kycDocNumber || !kycFilePath">
                    {{ kycUploading ? 'Upload...' : '1. Uploader le document' }}
                  </button>
                  <button (click)="verifyKyc()" class="btn-success" [disabled]="!kycUploaded">
                    {{ kycVerifying ? 'Vérification...' : '2. Simuler Validation Admin' }}
                  </button>
                </div>

                <p *ngIf="kycMsg" class="message success">{{ kycMsg }}</p>
                <p *ngIf="kycErr" class="message error">{{ kycErr }}</p>
              </div>
            </ng-template>
          </div>

          <!-- TAB CONTENT: OTP -->
          <div class="tab-content" *ngIf="activeTab === 'otp'">
            <h3>Sécurité à Double Facteur (OTP / SMS)</h3>
            <p class="description">Validez vos transactions importantes (telles que les débits E-cash importants ou les changements de sécurité) par code temporaire à usage unique.</p>
            
            <div class="otp-panel">
              <div class="form-group">
                <label>Numéro de téléphone pour réception de l'OTP</label>
                <input type="text" [(ngModel)]="otpPhone" [placeholder]="profile?.phone || '+212 600-000000'">
              </div>
              <button (click)="sendOtp()" [disabled]="otpSending">
                {{ otpSending ? 'Envoi...' : 'Envoyer un code de validation' }}
              </button>

              <div class="otp-verify-section" *ngIf="otpSent">
                <hr>
                <label>Entrez le code reçu (affiché ci-dessous pour démo)</label>
                <div class="otp-code-display" *ngIf="mockOtpCode">
                  🔑 Code OTP de démo généré par le serveur : <strong>{{ mockOtpCode }}</strong>
                </div>
                <input type="text" [(ngModel)]="otpCode" placeholder="Entrez le code à 6 chiffres">
                <button (click)="verifyOtp()" class="btn-success" [disabled]="otpVerifying || otpCode.length < 4">
                  {{ otpVerifying ? 'Validation...' : 'Valider le code' }}
                </button>
              </div>

              <p *ngIf="otpMsg" class="message success">{{ otpMsg }}</p>
              <p *ngIf="otpErr" class="message error">{{ otpErr }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(90deg, #b71c1c, #8e0000);
      color: white;
      padding: 3rem 2rem;
      text-align: center;
    }
    .page-header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; }
    .page-header p { opacity: 0.9; font-size: 1.1rem; }

    .container { max-width: 1200px; margin: 3rem auto; padding: 0 2rem; }

    .profile-layout {
      display: grid;
      grid-template-columns: 350px 1fr;
      gap: 2rem;
      align-items: start;
    }

    .profile-card {
      background: white;
      border-radius: 16px;
      padding: 2rem;
      box-shadow: 0 4px 15px rgba(0,0,0,0.08);
    }

    .avatar-section {
      text-align: center;
      border-bottom: 1px solid #eee;
      padding-bottom: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .avatar {
      font-size: 4rem;
      background: #ffebee;
      width: 100px;
      height: 100px;
      line-height: 100px;
      border-radius: 50%;
      margin: 0 auto 1rem;
    }
    .avatar-section h2 { color: #333; font-size: 1.4rem; margin-bottom: 0.5rem; }
    
    .role-badge {
      display: inline-block;
      background: #eceff1;
      color: #546e7a;
      padding: 0.25rem 0.75rem;
      border-radius: 50px;
      font-weight: bold;
      font-size: 0.8rem;
      margin-right: 0.5rem;
    }
    
    .member-badge {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      border-radius: 50px;
      font-weight: bold;
      font-size: 0.8rem;
    }
    .member-badge.ROUGE { background: #d32f2f; color: white; }
    .member-badge.OR { background: #ffc107; color: #333; }
    .member-badge.DIAMANT { background: #cfd8dc; color: #37474f; }
    .member-badge.JUNIOR { background: #00bcd4; color: white; }
    .member-badge.LEGENDE { background: #9c27b0; color: white; }

    .meta-info {
      font-size: 0.95rem;
      color: #555;
      line-height: 1.6;
      margin-bottom: 1.5rem;
    }
    .meta-info p { margin-bottom: 0.75rem; }
    .kyc-status { font-weight: bold; }
    .kyc-status.verified { color: #2e7d32; }

    .btn-danger-outline {
      width: 100%;
      padding: 0.75rem;
      background: transparent;
      border: 2px solid #d32f2f;
      color: #d32f2f;
      border-radius: 8px;
      font-weight: bold;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-danger-outline:hover {
      background: #d32f2f;
      color: white;
    }

    .main-content {
      background: white;
      border-radius: 16px;
      padding: 2rem;
      box-shadow: 0 4px 15px rgba(0,0,0,0.08);
      min-height: 500px;
    }

    .tabs {
      display: flex;
      gap: 0.5rem;
      border-bottom: 2px solid #eee;
      margin-bottom: 2rem;
      overflow-x: auto;
    }
    .tabs button {
      padding: 1rem 1.5rem;
      background: transparent;
      border: none;
      font-size: 1rem;
      font-weight: 500;
      color: #666;
      cursor: pointer;
      border-bottom: 3px solid transparent;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .tabs button:hover, .tabs button.active {
      color: #b71c1c;
      border-bottom-color: #b71c1c;
    }

    .tab-content h3 { color: #333; margin-bottom: 1.5rem; }
    .description { color: #666; font-size: 0.95rem; line-height: 1.5; margin-bottom: 1.5rem; }

    .form-row { display: flex; gap: 1rem; }
    .form-row .form-group { flex: 1; }
    .form-group { margin-bottom: 1.25rem; }
    label { display: block; margin-bottom: 0.5rem; color: #555; font-weight: 500; font-size: 0.9rem; }
    input, select, textarea {
      width: 100%;
      padding: 0.75rem;
      border: 2px solid #ddd;
      border-radius: 8px;
      font-size: 1rem;
      box-sizing: border-box;
    }
    input:focus, select:focus, textarea:focus { border-color: #d32f2f; outline: none; }

    button {
      padding: 0.8rem 1.8rem;
      background: #b71c1c;
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: bold;
      font-size: 1rem;
      cursor: pointer;
      transition: all 0.2s;
    }
    button:hover:not(:disabled) { background: #9e1c1c; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }

    .btn-success { background: #2e7d32; }
    .btn-success:hover:not(:disabled) { background: #1b5e20; }

    .btn-danger-small { background: #c62828; font-size: 0.85rem; padding: 0.5rem 1rem; }
    .btn-danger-small:hover { background: #b71c1c; }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      flex-wrap: wrap;
      gap: 1rem;
    }

    .message {
      margin-top: 1.5rem;
      padding: 1rem;
      border-radius: 8px;
      font-weight: 500;
      text-align: center;
    }
    .message.success { background: #e8f5e9; color: #2e7d32; }
    .message.error { background: #ffebee; color: #c62828; }

    /* SESSIONS LIST */
    .sessions-list { display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem; }
    .session-item {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem;
      border: 1px solid #eee;
      border-radius: 8px;
      transition: all 0.2s;
    }
    .session-item.current { background: #f1f8e9; border-color: #c5e1a5; }
    .session-icon { font-size: 2rem; }
    .session-details { flex: 1; }
    .session-title { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem; }
    .current-tag { background: #689f38; color: white; font-size: 0.7rem; padding: 0.15rem 0.5rem; border-radius: 50px; font-weight: bold; }
    .session-meta { display: flex; gap: 1.5rem; color: #888; font-size: 0.85rem; }
    .btn-revoke { background: transparent; border: 1.5px solid #c62828; color: #c62828; padding: 0.4rem 1rem; border-radius: 6px; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; }
    .btn-revoke:hover { background: #c62828; color: white; }

    /* KYC PANEL */
    .kyc-badge { display: flex; gap: 1rem; align-items: center; padding: 1.5rem; border-radius: 8px; }
    .kyc-badge.verified { background: #e8f5e9; border: 1px solid #a5d6a7; color: #1b5e20; }
    .kyc-badge .icon { font-size: 2.5rem; }
    .actions-kyc { display: flex; gap: 1rem; margin-top: 1rem; }

    /* OTP PANEL */
    .otp-code-display {
      background: #e1f5fe;
      border: 1px solid #81d4fa;
      color: #0277bd;
      padding: 1rem;
      border-radius: 8px;
      margin-bottom: 1.5rem;
      font-size: 0.95rem;
    }
    .otp-verify-section { margin-top: 1.5rem; }
    .otp-verify-section input { width: 100%; max-width: 300px; margin-bottom: 1rem; display: block; }
  `]
})
export class ProfilComponent implements OnInit {
  profile: any = null;
  sessions: any[] = [];
  activeTab = 'info';

  // Forms edit profile
  editFirstName = '';
  editLastName = '';
  editPhone = '';
  editVille = '';
  editLangue = '';
  editBio = '';
  saving = false;
  infoMsg = '';
  infoErr = '';

  // Forms KYC
  kycDocType = 'CIN';
  kycDocNumber = '';
  kycFilePath = '';
  kycUploading = false;
  kycVerifying = false;
  kycUploaded = false;
  kycMsg = '';
  kycErr = '';

  // Forms OTP
  otpPhone = '';
  otpCode = '';
  mockOtpCode = '';
  otpSending = false;
  otpVerifying = false;
  otpSent = false;
  otpMsg = '';
  otpErr = '';

  // Common
  sessionMsg = '';

  authService = inject(AuthService);
  router = inject(Router);

  ngOnInit() {
    this.loadProfile();
    this.loadSessions();
  }

  loadProfile() {
    this.authService.getProfile().subscribe({
      next: (data) => {
        this.profile = data;
        this.editFirstName = data.firstName;
        this.editLastName = data.lastName;
        this.editPhone = data.phone || '';
        this.otpPhone = data.phone || '';
      },
      error: () => this.profile = null
    });
  }

  loadSessions() {
    const currentToken = localStorage.getItem('wydad_token');
    this.authService.getSessions().subscribe({
      next: (data: any[]) => {
        // Le backend retourne la liste des sessions. Essayons d'identifier la session courante.
        this.sessions = data.map(s => ({
          ...s,
          isCurrent: s.token === currentToken || data.indexOf(s) === 0 // mock simple fallback
        }));
      },
      error: () => this.sessions = []
    });
  }

  updateProfile() {
    this.saving = true;
    this.infoMsg = '';
    this.infoErr = '';

    const updateData = {
      email: this.profile.email,
      firstName: this.editFirstName,
      lastName: this.editLastName,
      phone: this.editPhone,
      ville: this.editVille,
      langue: this.editLangue,
      timezone: 'UTC+1',
      bio: this.editBio
    };

    this.authService.updateProfile(updateData).subscribe({
      next: (res) => {
        this.saving = false;
        this.infoMsg = 'Informations de profil mises à jour avec succès !';
        this.loadProfile();
        // Mettre à jour localstorage
        localStorage.setItem('wydad_first_name', this.editFirstName);
        localStorage.setItem('wydad_last_name', this.editLastName);
      },
      error: (err) => {
        this.saving = false;
        this.infoErr = err.error?.message || 'Erreur lors de la mise à jour du profil.';
      }
    });
  }

  deleteAccount() {
    if (confirm('Êtes-vous sûr de vouloir supprimer définitivement votre compte ? Cette action est irréversible.')) {
      this.authService.deleteAccount().subscribe({
        next: () => {
          alert('Votre compte a été supprimé.');
          this.authService.logout();
          this.router.navigate(['/']);
        },
        error: () => alert('Erreur lors de la suppression du compte.')
      });
    }
  }

  revokeSession(sessionId: string) {
    this.authService.revokeSession(sessionId).subscribe({
      next: () => {
        this.sessionMsg = 'Session révoquée avec succès.';
        this.loadSessions();
        setTimeout(() => this.sessionMsg = '', 3000);
      }
    });
  }

  revokeAllSessions() {
    if (confirm('Voulez-vous déconnecter tous vos autres appareils connectés ?')) {
      this.authService.revokeAllSessions().subscribe({
        next: () => {
          this.sessionMsg = 'Toutes les autres sessions ont été révoquées.';
          this.loadSessions();
          setTimeout(() => this.sessionMsg = '', 3000);
        }
      });
    }
  }

  uploadKyc() {
    this.kycUploading = true;
    this.kycMsg = '';
    this.kycErr = '';

    this.authService.uploadKyc(this.kycDocType, this.kycDocNumber, this.kycFilePath).subscribe({
      next: () => {
        this.kycUploading = false;
        this.kycUploaded = true;
        this.kycMsg = 'Document téléversé avec succès. Prêt pour la vérification administrative.';
      },
      error: (err) => {
        this.kycUploading = false;
        this.kycErr = err.error?.message || "Erreur de téléversement du document.";
      }
    });
  }

  verifyKyc() {
    this.kycVerifying = true;
    this.kycMsg = '';
    this.kycErr = '';

    this.authService.verifyKycMock(this.profile.email).subscribe({
      next: () => {
        this.kycVerifying = false;
        this.kycMsg = 'Compte validé administrativement avec succès !';
        this.loadProfile();
      },
      error: (err) => {
        this.kycVerifying = false;
        this.kycErr = err.error?.message || 'Erreur lors de la validation.';
      }
    });
  }

  sendOtp() {
    this.otpSending = true;
    this.otpMsg = '';
    this.otpErr = '';
    this.otpSent = false;

    this.authService.sendOtp(this.otpPhone).subscribe({
      next: (res) => {
        this.otpSending = false;
        this.otpSent = true;
        this.otpMsg = 'Un code de test a été généré par le serveur.';
        // Extraire le code mock de la réponse s'il est renvoyé ("Code OTP généré (mock): 123456")
        const match = res.match(/mock\):\s*(\d+)/);
        if (match) {
          this.mockOtpCode = match[1];
        }
      },
      error: (err) => {
        this.otpSending = false;
        this.otpErr = err.error?.message || 'Erreur lors de l\'envoi de l\'OTP.';
      }
    });
  }

  verifyOtp() {
    this.otpVerifying = true;
    this.otpMsg = '';
    this.otpErr = '';

    this.authService.verifyOtp(this.otpCode).subscribe({
      next: (res) => {
        this.otpVerifying = false;
        this.otpMsg = '✅ Code OTP validé avec succès ! Votre numéro est vérifié.';
        this.otpSent = false;
        this.otpCode = '';
        this.mockOtpCode = '';
      },
      error: (err) => {
        this.otpVerifying = false;
        this.otpErr = err.error?.message || 'Code OTP incorrect ou expiré.';
      }
    });
  }
}
