import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-carte-membre',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-header">
      <h1>🎟️ Carte Membre Digitale</h1>
      <p>Votre passeport Wydad AC, toujours dans votre poche</p>
    </div>

    <div class="container" *ngIf="cardData; else notConnected">
      <div class="card-visual">
        <div class="card-header">
          <span class="logo">🔴⚫ WYDAD AC</span>
          <span class="level" [class]="cardData.membershipLevel">{{ cardData.membershipLevel }}</span>
        </div>
        <div class="card-body">
          <div class="qr-section">
            <img [src]="'data:image/png;base64,' + cardData.qrCodeBase64" alt="QR Code">
            <p>Scannez pour vérifier</p>
          </div>
          <div class="info-section">
            <h2>{{ cardData.firstName }} {{ cardData.lastName }}</h2>
            <p class="email">{{ cardData.email }}</p>
            <p class="code">Code parrainage : <strong>{{ cardData.referralCode }}</strong></p>
          </div>
        </div>
      </div>

      <!-- MEMBER ACTIONS -->
      <div class="actions-section">
        <!-- PDF CERTIFICATE -->
        <div class="action-card">
          <h3>📜 Attestation officielle</h3>
          <p>Téléchargez votre certificat officiel d'adhésion au Wydad Athletic Club au format PDF signé.</p>
          <button (click)="downloadAttestation()" [disabled]="pdfLoading">
            {{ pdfLoading ? 'Génération...' : '⬇️ Télécharger le PDF' }}
          </button>
        </div>

        <!-- MEMBERSHIP UPGRADE -->
        <div class="action-card">
          <h3>📈 Augmenter mon niveau</h3>
          <p>Mettez à niveau votre adhésion pour débloquer plus d'avantages exclusifs et soutenir le club.</p>
          <div class="upgrade-options" *ngIf="canUpgrade()">
            <button *ngIf="isLevel('ROUGE') || isLevel('JUNIOR')" (click)="upgrade('OR')" class="btn-upgrade-or">
              Mise à niveau OR (1200 DH)
            </button>
            <button *ngIf="isLevel('ROUGE') || isLevel('OR') || isLevel('JUNIOR')" (click)="upgrade('DIAMANT')" class="btn-upgrade-diamant">
              Mise à niveau DIAMANT (3000 DH)
            </button>
          </div>
          <p *ngIf="!canUpgrade()" class="max-level-msg">🎉 Vous bénéficiez déjà du niveau d'adhésion maximal ! Merci de votre soutien.</p>
          <p *ngIf="upgradeMsg" class="success-msg">{{ upgradeMsg }}</p>
          <p *ngIf="upgradeErr" class="error-msg">{{ upgradeErr }}</p>
        </div>
      </div>
    </div>

    <ng-template #notConnected>
      <div class="container not-connected">
        <div class="lock-icon">🔒</div>
        <h2>Espace réservé aux adhérents</h2>
        <p>Connectez-vous pour accéder à votre carte membre digitale.</p>
        <button (click)="goLogin()">Se connecter</button>
      </div>
    </ng-template>
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

    .container { max-width: 800px; margin: 3rem auto; padding: 0 2rem; }

    .card-visual {
      background: linear-gradient(135deg, #b71c1c 0%, #7f0000 50%, #000 100%);
      border-radius: 20px;
      padding: 2.5rem;
      color: white;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
      margin-bottom: 3rem;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
      border-bottom: 1px solid rgba(255,255,255,0.2);
      padding-bottom: 1rem;
    }
    .logo { font-weight: 900; font-size: 1.3rem; letter-spacing: 2px; }
    
    .level {
      padding: 0.4rem 1.2rem;
      border-radius: 50px;
      font-weight: bold;
      font-size: 0.85rem;
    }
    .level.ROUGE { background: #d32f2f; color: white; }
    .level.OR { background: #ffc107; color: #333; }
    .level.DIAMANT { background: #e0e0e0; color: #333; }
    .level.JUNIOR { background: #00bcd4; color: white; }
    .level.LEGENDE { background: #9c27b0; color: white; }

    .card-body {
      display: flex;
      gap: 3rem;
      align-items: center;
      flex-wrap: wrap;
      justify-content: center;
    }
    .qr-section { text-align: center; }
    .qr-section img { width: 180px; height: 180px; border-radius: 12px; border: 4px solid white; }
    .qr-section p { margin-top: 0.5rem; opacity: 0.8; font-size: 0.85rem; }
    .info-section h2 { font-size: 2rem; margin-bottom: 0.5rem; font-weight: 800; }
    .info-section .email { opacity: 0.8; margin-bottom: 1.5rem; font-size: 1.1rem; }
    .info-section .code { background: rgba(255,255,255,0.15); padding: 0.75rem 1.25rem; border-radius: 8px; font-size: 1rem; }

    /* ACTIONS SECTION */
    .actions-section {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 2rem;
    }
    .action-card {
      background: white;
      border-radius: 16px;
      padding: 1.5rem;
      box-shadow: 0 4px 15px rgba(0,0,0,0.05);
      border-top: 4px solid #b71c1c;
      display: flex;
      flex-direction: column;
    }
    .action-card h3 { color: #333; margin-bottom: 0.5rem; font-size: 1.2rem; }
    .action-card p { color: #666; font-size: 0.9rem; line-height: 1.4; margin-bottom: 1.5rem; flex: 1; }
    
    .action-card button {
      background: #b71c1c;
      color: white;
      border: none;
      padding: 0.8rem;
      border-radius: 8px;
      font-weight: bold;
      cursor: pointer;
      transition: background 0.2s;
    }
    .action-card button:hover:not(:disabled) { background: #9e1c1c; }
    .action-card button:disabled { opacity: 0.6; cursor: not-allowed; }

    .upgrade-options {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .btn-upgrade-or { background: #ffc107 !important; color: #333 !important; }
    .btn-upgrade-or:hover { background: #ffb300 !important; }
    .btn-upgrade-diamant { background: #78909c !important; color: white !important; }
    .btn-upgrade-diamant:hover { background: #607d8b !important; }

    .max-level-msg { color: #2e7d32; font-weight: bold; font-size: 0.9rem; text-align: center; }
    .success-msg { color: #2e7d32; font-weight: 500; font-size: 0.9rem; margin-top: 0.75rem; text-align: center; }
    .error-msg { color: #c62828; font-weight: 500; font-size: 0.9rem; margin-top: 0.75rem; text-align: center; }

    .not-connected { text-align: center; padding: 4rem 2rem; }
    .lock-icon { font-size: 4rem; margin-bottom: 1rem; }
    .not-connected h2 { color: #333; margin-bottom: 0.5rem; }
    .not-connected p { color: #666; margin-bottom: 2rem; }
    .not-connected button {
      background: linear-gradient(90deg, #d32f2f, #b71c1c);
      color: white;
      border: none;
      padding: 1rem 2.5rem;
      border-radius: 50px;
      font-size: 1rem;
      font-weight: bold;
      cursor: pointer;
      transition: transform 0.3s;
    }
    .not-connected button:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(211,47,47,0.4); }
  `]
})
export class CarteMembreComponent implements OnInit {
  cardData: any = null;
  pdfLoading = false;
  upgradeMsg = '';
  upgradeErr = '';

  api = inject(ApiService);
  router = inject(Router);

  ngOnInit() {
    this.loadCard();
  }

  loadCard() {
    const email = localStorage.getItem('wydad_email');
    if (email) {
      this.api.getMemberCard(email).subscribe({
        next: (data) => this.cardData = data,
        error: () => this.cardData = null
      });
    }
  }

  downloadAttestation() {
    const email = localStorage.getItem('wydad_email');
    if (!email) return;
    this.pdfLoading = true;
    this.api.getAttestation(email).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'attestation-wac.pdf';
        a.click();
        window.URL.revokeObjectURL(url);
        this.pdfLoading = false;
      },
      error: () => {
        alert('Erreur lors du téléchargement de l\'attestation.');
        this.pdfLoading = false;
      }
    });
  }

  isLevel(level: string): boolean {
    return this.cardData?.membershipLevel === level;
  }

  canUpgrade(): boolean {
    if (!this.cardData) return false;
    const lvl = this.cardData.membershipLevel;
    return lvl === 'ROUGE' || lvl === 'JUNIOR' || lvl === 'OR';
  }

  upgrade(newLevel: string) {
    const email = localStorage.getItem('wydad_email');
    if (!email) return;
    
    this.upgradeMsg = '';
    this.upgradeErr = '';
    
    this.api.upgradeMembership(email, newLevel).subscribe({
      next: (res) => {
        this.upgradeMsg = `Félicitations ! Votre adhésion a été mise à niveau vers le niveau ${newLevel}.`;
        this.loadCard();
      },
      error: (err) => {
        this.upgradeErr = err.error?.message || 'Erreur lors de la mise à niveau.';
      }
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }
}