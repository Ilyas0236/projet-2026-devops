import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-ecash',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ecash.component.html',
  styles: []
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