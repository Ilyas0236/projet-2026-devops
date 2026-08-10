import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-ecash',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-header">
      <h1>💰 Porte-Monnaie E-Cash</h1>
      <p>Votre solde, vos transactions, vos dons et rechargements sécurisés</p>
    </div>

    <div class="container" *ngIf="balance; else notConnected">
      <!-- SOLDE CARD -->
      <div class="balance-card">
        <div class="balance-label">Solde disponible</div>
        <div class="balance-amount">{{ balance.balance | number:'1.2-2' }} MAD</div>
        <div class="balance-date">Mis à jour le {{ balance.updatedAt | date:'dd/MM/yyyy HH:mm' }}</div>
      </div>

      <!-- MAIN ACTIONS -->
      <div class="actions">
        <button class="btn-primary" (click)="toggleActionForm('don')">❤️ Faire un don au club</button>
        <button class="btn-success" (click)="toggleActionForm('credit')">💳 Recharger mon solde</button>
      </div>

      <!-- DONATION FORM -->
      <div class="action-form don-form" *ngIf="activeForm === 'don'">
        <h3>❤️ Faire un don au Wydad AC</h3>
        <p class="form-desc">Soutenez les projets d'infrastructure et de formation du club.</p>
        <div class="form-group">
          <label>Montant (MAD)</label>
          <input type="number" [(ngModel)]="donAmount" placeholder="Montant (MAD)" min="10">
        </div>
        <div class="don-options">
          <label><input type="radio" name="donType" [(ngModel)]="donType" value="PONCTUEL"> Ponctuel</label>
          <label><input type="radio" name="donType" [(ngModel)]="donType" value="MENSUEL"> Mensuel</label>
        </div>
        <label class="checkbox"><input type="checkbox" [(ngModel)]="recuFiscal"> Recevoir un reçu fiscal officiel (PDF)</label>
        <button (click)="makeDon()" [disabled]="donAmount < 10 || processing">
          {{ processing ? 'Validation du don...' : 'Confirmer mon don' }}
        </button>
        <p *ngIf="donMessage" class="message success">{{ donMessage }}</p>
      </div>

      <!-- CREDIT FORM -->
      <div class="action-form credit-form" *ngIf="activeForm === 'credit'">
        <h3>💳 Recharger le portefeuille E-Cash</h3>
        
        <!-- MODE SELECT -->
        <div class="mode-select">
          <button (click)="creditMode = 'card'" [class.active]="creditMode === 'card'">Paiement Carte Bancaire</button>
          <button (click)="creditMode = 'direct'" [class.active]="creditMode === 'direct'">Crédit Direct (Démo)</button>
        </div>

        <!-- MODE 1: BANK CARD -->
        <div *ngIf="creditMode === 'card'" class="card-form">
          <p class="form-desc">Simulez une transaction bancaire sécurisée via la passerelle ChariBaaS.</p>
          <div class="form-group">
            <label>Montant à recharger (MAD)</label>
            <input type="number" [(ngModel)]="cardAmount" placeholder="Montant (MAD)" min="1">
          </div>
          <div class="form-group">
            <label>Numéro de carte bancaire</label>
            <input type="text" [(ngModel)]="cardNumber" placeholder="1234567812345678" maxlength="16">
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Date d'expiration</label>
              <input type="text" [(ngModel)]="cardExpiry" placeholder="MM/AA" maxlength="5">
            </div>
            <div class="form-group">
              <label>CVV</label>
              <input type="password" [(ngModel)]="cardCvv" placeholder="123" maxlength="3">
            </div>
          </div>
          <div class="form-group">
            <label>Code OTP de test (requis : 6 chiffres)</label>
            <input type="text" [(ngModel)]="cardOtp" placeholder="123456" maxlength="6">
            <small class="help-text">Saisissez n'importe quel code à 6 chiffres pour la simulation.</small>
          </div>

          <button (click)="payByCard()" [disabled]="processing || !isValidCardForm()">
            {{ processing ? 'Traitement bancaire...' : 'Payer et recharger' }}
          </button>
        </div>

        <!-- MODE 2: DIRECT CREDIT -->
        <div *ngIf="creditMode === 'direct'">
          <p class="form-desc">Créditez directement le solde sans validation bancaire (idéal pour tester rapidement).</p>
          <div class="form-group">
            <label>Montant (MAD)</label>
            <input type="number" [(ngModel)]="directAmount" placeholder="Montant (MAD)" min="1">
          </div>
          <div class="form-group">
            <label>Description du rechargement</label>
            <input type="text" [(ngModel)]="directDescription" placeholder="Ex: Recharge de test">
          </div>
          <button (click)="creditDirect()" [disabled]="processing || directAmount < 1">
            {{ processing ? 'Crédit en cours...' : 'Créditer immédiatement' }}
          </button>
        </div>

        <p *ngIf="creditMessage" class="message success">{{ creditMessage }}</p>
        <p *ngIf="creditError" class="message error">{{ creditError }}</p>
      </div>

      <!-- TRANSACTIONS LIST -->
      <h2 class="section-title">📜 Historique des transactions</h2>
      <div class="transactions">
        <div class="tx" *ngFor="let tx of transactions">
          <div class="tx-icon" [class.credit]="tx.type === 'CREDIT'" [class.debit]="tx.type === 'DEBIT'" [class.don]="tx.type === 'DON'">
            {{ tx.type === 'CREDIT' ? '⬇️' : tx.type === 'DEBIT' ? '⬆️' : '❤️' }}
          </div>
          <div class="tx-info">
            <div class="tx-desc">{{ tx.description }}</div>
            <div class="tx-ref">Réf : <code>{{ tx.reference }}</code></div>
            <div class="tx-date">{{ tx.createdAt | date:'dd/MM/yyyy HH:mm' }}</div>
          </div>
          <div class="tx-amount" [class.negative]="tx.type === 'DEBIT' || tx.type === 'DON'">
            {{ tx.type === 'CREDIT' ? '+' : '-' }}{{ tx.amount | number:'1.2-2' }} MAD
          </div>
        </div>
        <div *ngIf="transactions.length === 0" class="empty-tx">
          Aucune transaction pour le moment.
        </div>
      </div>
    </div>

    <ng-template #notConnected>
      <div class="container not-connected">
        <div class="lock-icon">🔒</div>
        <h2>Connectez-vous pour accéder à votre E-Cash</h2>
        <p>Le porte-monnaie E-Cash est réservé aux adhérents.</p>
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

    .container { max-width: 900px; margin: 0 auto; padding: 2rem; }

    .balance-card {
      background: linear-gradient(135deg, #2e7d32, #1b5e20);
      color: white;
      border-radius: 20px;
      padding: 2.5rem;
      text-align: center;
      margin-bottom: 2.5rem;
      box-shadow: 0 10px 40px rgba(46,125,50,0.3);
    }
    .balance-label { font-size: 1rem; opacity: 0.9; margin-bottom: 0.5rem; }
    .balance-amount { font-size: 3.5rem; font-weight: 900; margin-bottom: 0.5rem; }
    .balance-date { font-size: 0.9rem; opacity: 0.8; }

    .actions { display: flex; gap: 1.5rem; justify-content: center; margin-bottom: 2.5rem; }
    
    .btn-primary, .btn-success {
      color: white;
      border: none;
      padding: 1rem 2.5rem;
      border-radius: 50px;
      font-size: 1rem;
      font-weight: bold;
      cursor: pointer;
      transition: transform 0.3s, box-shadow 0.3s;
    }
    .btn-primary { background: linear-gradient(90deg, #d32f2f, #b71c1c); }
    .btn-primary:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(211,47,47,0.3); }
    
    .btn-success { background: linear-gradient(90deg, #4caf50, #2e7d32); }
    .btn-success:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(76,175,80,0.3); }

    .action-form {
      background: white;
      border-radius: 16px;
      padding: 2.5rem;
      margin-bottom: 2.5rem;
      box-shadow: 0 4px 20px rgba(0,0,0,0.06);
      border-top: 5px solid #b71c1c;
    }
    .credit-form { border-top-color: #2e7d32; }
    .action-form h3 { color: #333; margin-bottom: 0.25rem; font-size: 1.4rem; }
    .form-desc { color: #666; font-size: 0.9rem; margin-bottom: 1.5rem; }

    .form-row { display: flex; gap: 1rem; }
    .form-row .form-group { flex: 1; }
    .form-group { margin-bottom: 1.25rem; }
    label { display: block; margin-bottom: 0.5rem; color: #555; font-weight: 500; font-size: 0.9rem; }
    input {
      width: 100%;
      padding: 0.75rem;
      border: 2px solid #ddd;
      border-radius: 8px;
      font-size: 1rem;
      box-sizing: border-box;
    }
    input:focus { border-color: #d32f2f; outline: none; }
    .credit-form input:focus { border-color: #2e7d32; }

    .action-form button {
      width: 100%;
      padding: 0.9rem;
      background: #b71c1c;
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: bold;
      font-size: 1.05rem;
      cursor: pointer;
      transition: background 0.2s;
    }
    .action-form button:hover:not(:disabled) { background: #9e1c1c; }
    .credit-form button { background: #2e7d32; }
    .credit-form button:hover:not(:disabled) { background: #1b5e20; }
    .action-form button:disabled { opacity: 0.6; cursor: not-allowed; }

    .don-options { display: flex; gap: 1.5rem; margin-bottom: 1.25rem; }
    .don-options label { cursor: pointer; color: #555; display: flex; align-items: center; gap: 0.5rem; }
    .checkbox { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1.5rem; color: #555; cursor: pointer; }
    .checkbox input { width: auto; }

    /* MODE SELECT */
    .mode-select { display: flex; gap: 0.5rem; margin-bottom: 1.5rem; border-bottom: 1px solid #eee; padding-bottom: 1rem; }
    .mode-select button {
      background: transparent;
      color: #666;
      border: 1px solid #ccc;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-size: 0.9rem;
      cursor: pointer;
      width: auto;
      font-weight: 500;
    }
    .mode-select button.active {
      background: #e8f5e9;
      color: #2e7d32;
      border-color: #a5d6a7;
    }
    .help-text { color: #888; font-size: 0.8rem; display: block; margin-top: 0.25rem; }

    .message { margin-top: 1.5rem; padding: 1rem; border-radius: 8px; font-weight: 500; text-align: center; }
    .message.success { background: #e8f5e9; color: #2e7d32; }
    .message.error { background: #ffebee; color: #c62828; }

    .section-title { color: #333; margin: 2.5rem 0 1.25rem; font-size: 1.6rem; font-weight: 700; }

    /* TRANSACTIONS */
    .transactions { display: flex; flex-direction: column; gap: 0.75rem; }
    .tx {
      background: white;
      border-radius: 12px;
      padding: 1.25rem 1.5rem;
      display: flex;
      align-items: center;
      gap: 1.25rem;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
    }
    .tx-icon { font-size: 1.6rem; width: 45px; height: 45px; line-height: 45px; border-radius: 50%; text-align: center; background: #f5f5f5; }
    .tx-icon.credit { background: #e8f5e9; color: #2e7d32; }
    .tx-icon.debit { background: #ffebee; color: #c62828; }
    .tx-icon.don { background: #fce4ec; color: #c2185b; }
    .tx-info { flex: 1; }
    .tx-desc { font-weight: 600; color: #333; font-size: 1rem; margin-bottom: 0.2rem; }
    .tx-ref { font-size: 0.8rem; color: #777; margin-bottom: 0.1rem; }
    .tx-ref code { background: #f5f5f5; padding: 0.1rem 0.3rem; border-radius: 4px; }
    .tx-date { font-size: 0.8rem; color: #aaa; }
    .tx-amount { font-weight: 800; font-size: 1.2rem; color: #2e7d32; }
    .tx-amount.negative { color: #c62828; }
    .empty-tx { text-align: center; padding: 3rem; color: #999; }

    .not-connected { text-align: center; padding: 5rem 2rem; }
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
    }
  `]
})
export class EcashComponent implements OnInit {
  balance: any = null;
  transactions: any[] = [];
  activeForm: 'don' | 'credit' | null = null;
  processing = false;

  // Donation Form
  donAmount = 100;
  donType = 'PONCTUEL';
  recuFiscal = true;
  donMessage = '';

  // Credit Form
  creditMode: 'card' | 'direct' = 'card';
  creditMessage = '';
  creditError = '';
  
  // Credit Mode Card
  cardAmount = 200;
  cardNumber = '';
  cardExpiry = '';
  cardCvv = '';
  cardOtp = '';

  // Credit Mode Direct
  directAmount = 500;
  directDescription = '';

  api = inject(ApiService);
  router = inject(Router);

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    const email = localStorage.getItem('wydad_email');
    if (email) {
      this.api.getBalance(email).subscribe({
        next: (data) => this.balance = data,
        error: () => this.balance = null
      });
      this.api.getTransactions(email).subscribe({
        next: (data) => {
          this.transactions = data.sort((a, b) => b.id - a.id); // trier par id décroissant (les plus récents en premier)
        },
        error: () => this.transactions = []
      });
    }
  }

  toggleActionForm(form: 'don' | 'credit') {
    if (this.activeForm === form) {
      this.activeForm = null;
    } else {
      this.activeForm = form;
      this.donMessage = '';
      this.creditMessage = '';
      this.creditError = '';
    }
  }

  makeDon() {
    const email = localStorage.getItem('wydad_email');
    if (!email || this.donAmount < 10) return;
    
    this.processing = true;
    this.donMessage = '';
    
    this.api.makeDon(email, this.donAmount, this.donType, this.recuFiscal).subscribe({
      next: (blob: Blob) => {
        this.processing = false;
        if (this.recuFiscal && blob.type === 'application/pdf') {
          // Déclencher le téléchargement du reçu fiscal PDF
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `recu-fiscal-${Date.now()}.pdf`;
          a.click();
          window.URL.revokeObjectURL(url);
          this.donMessage = '✅ Don validé avec succès ! Votre reçu fiscal PDF a été téléchargé.';
        } else {
          this.donMessage = '✅ Don validé avec succès ! Merci infiniment pour votre générosité.';
        }
        this.activeForm = null;
        this.loadData();
      },
      error: (err) => {
        this.processing = false;
        this.donMessage = '❌ Erreur lors du don. Assurez-vous d\'avoir un solde suffisant.';
      }
    });
  }

  isValidCardForm(): boolean {
    return (
      this.cardAmount >= 1 &&
      /^\d{16}$/.test(this.cardNumber) &&
      /^\d{2}\/\d{2}$/.test(this.cardExpiry) &&
      /^\d{3}$/.test(this.cardCvv) &&
      /^\d{6}$/.test(this.cardOtp)
    );
  }

  payByCard() {
    const email = localStorage.getItem('wydad_email');
    if (!email) return;

    this.processing = true;
    this.creditMessage = '';
    this.creditError = '';

    const cardInfo = {
      cardNumber: this.cardNumber,
      expiryDate: this.cardExpiry,
      cvv: this.cardCvv,
      otp: this.cardOtp,
      amount: this.cardAmount
    };

    this.api.payByCard(email, cardInfo).subscribe({
      next: (res) => {
        this.processing = false;
        this.creditMessage = `✅ Rechargement de ${this.cardAmount} MAD réussi par carte bancaire ! (Réf: ${res.reference})`;
        this.cardNumber = '';
        this.cardExpiry = '';
        this.cardCvv = '';
        this.cardOtp = '';
        this.activeForm = null;
        this.loadData();
      },
      error: (err) => {
        this.processing = false;
        this.creditError = err.error?.message || 'Transaction bancaire rejetée par la passerelle de paiement.';
      }
    });
  }

  creditDirect() {
    const email = localStorage.getItem('wydad_email');
    if (!email || this.directAmount < 1) return;

    this.processing = true;
    this.creditMessage = '';
    this.creditError = '';

    this.api.creditWallet(email, this.directAmount, this.directDescription || 'Crédit direct démo').subscribe({
      next: (res) => {
        this.processing = false;
        this.creditMessage = `✅ Crédit de ${this.directAmount} MAD appliqué immédiatement ! (Réf: ${res.reference})`;
        this.directDescription = '';
        this.activeForm = null;
        this.loadData();
      },
      error: (err) => {
        this.processing = false;
        this.creditError = err.error?.message || 'Erreur lors de la transaction de crédit.';
      }
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }
}