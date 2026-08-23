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
  /** Paliers payants depuis la configuration club (source de verite ADMIN). */
  tiers: any[] = [];

  api = inject(ApiService);
  router = inject(Router);
  toast = inject(ToastService);

  ngOnInit() {
    this.loadCard();
    this.api.getClubSetting('membership_tiers').subscribe({
      next: (tiers) => {
        this.tiers = Array.isArray(tiers) ? tiers.filter(t => t.price != null) : [];
      },
      error: () => {
        this.tiers = [];
      }
    });
  }

  tierPrice(level: string): number | null {
    const t = this.tiers.find(x => x.level === level);
    return t ? t.price : null;
  }

  /** Paliers strictement superieurs au niveau actuel (hors LEGENDE sur invitation). */
  upgradableTiers(): any[] {
    if (!this.cardData) return [];
    const currentIdx = this.tiers.findIndex(t => t.level === this.cardData.membershipLevel);
    return currentIdx < 0
      ? []
      : this.tiers.slice(currentIdx + 1).filter(t => t.level !== 'LEGENDE');
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