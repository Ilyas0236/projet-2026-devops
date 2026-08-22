import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-don',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './don.component.html',
  styles: []
})
export class DonComponent implements OnInit {
  email = '';
  amount = 100;
  type = 'PONCTUEL';
  message = '';
  recuFiscal = true;
  loading = false;
  successMsg = '';
  errorMsg = '';

  presetAmounts = [50, 100, 200, 500];

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.email = localStorage.getItem('wydad_email') || '';
    if (!this.email) {
      // La route /don est protégée par authGuard ; sécurité supplémentaire
      window.location.href = '/login';
    }
  }

  selectAmount(a: number) {
    this.amount = a;
  }

  submit() {
    this.errorMsg = '';
    this.successMsg = '';

    if (!this.amount || this.amount < 10) {
      this.errorMsg = 'Le montant minimum est de 10 MAD.';
      return;
    }

    this.loading = true;
    this.api.makeDon(this.email, this.amount, this.type, this.recuFiscal).subscribe({
      next: (blob) => {
        this.loading = false;
        this.successMsg = 'Merci pour votre don ! Votre soutien compte pour le club. 🏆';
        if (blob && blob.size > 0) {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'recu-fiscal-wac.pdf';
          a.click();
          window.URL.revokeObjectURL(url);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err.error?.message || 'Erreur lors du traitement du don. Veuillez réessayer.';
      }
    });
  }
}
