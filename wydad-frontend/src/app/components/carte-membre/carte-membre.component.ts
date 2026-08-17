import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

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