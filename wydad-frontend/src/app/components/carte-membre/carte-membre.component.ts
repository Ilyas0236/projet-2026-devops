import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-carte-membre',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './carte-membre.component.html',
  styles: []
})
export class CarteMembreComponent implements OnInit {
  cardData: any = null;
  pdfLoading = false;
  upgradeMsg = '';
  upgradeErr = '';

  api = inject(ApiService);
  router = inject(Router);
  toast = inject(ToastService);

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
        this.toast.error('Erreur lors du téléchargement de l\'attestation.');
        this.pdfLoading = false;
      }
    });
  }

  isLevel(level: string): boolean {
    return this.cardData?.membershipLevel === level;
  }

  goLogin() {
    this.router.navigate(['/login']);
  }
}