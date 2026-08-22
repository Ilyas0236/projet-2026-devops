import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';

/**
 * Panneau d'administration des parametres club (source de verite metier).
 * Edition JSON des cles connues : membership_tiers (paliers d'adhesion)
 * et club_info (coordonnees). Toute modification impacte directement le
 * site public (homepage, adhesion, footer).
 */
@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Paramètres du Club</h2>
        <p class="text-sm text-gray-400 mt-1">
          Configuration métier du site : paliers d'adhésion, coordonnées. Ces contenus alimentent la homepage, la page adhésion et le footer.
        </p>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading" class="space-y-4">
        <div *ngFor="let s of settings" class="bg-white/5 border border-white/10 rounded-lg p-5">
          <div class="flex justify-between items-center mb-3">
            <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm">{{ labelFor(s.key) }}</h3>
            <span class="text-xs text-gray-500 font-mono">{{ s.key }}</span>
          </div>

          <textarea
            [(ngModel)]="drafts[s.key]"
            rows="{{ s.key === 'membership_tiers' ? 14 : 8 }}"
            class="w-full bg-black border border-white/10 rounded px-3 py-2 text-sm text-white font-mono focus:border-wydad-red focus:outline-none"
            spellcheck="false"></textarea>

          <div class="flex justify-between items-center mt-3">
            <span *ngIf="validationErrors[s.key]" class="text-xs text-red-400">JSON invalide — correction requise avant enregistrement</span>
            <span *ngIf="!validationErrors[s.key]" class="text-xs text-gray-600">Modifiez puis enregistrez. Impact immédiat sur le site public.</span>
            <button (click)="save(s.key)"
                    [disabled]="validationErrors[s.key]"
                    class="px-4 py-2 bg-wydad-red disabled:opacity-40 disabled:cursor-not-allowed hover:bg-red-700 text-white uppercase text-xs font-bold tracking-wider">
              Enregistrer
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSettingsComponent implements OnInit {
  settings: { key: string; value: any }[] = [];
  drafts: Record<string, string> = {};
  validationErrors: Record<string, boolean> = {};
  loading = true;
  error = '';

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadSettings();
  }

  loadSettings() {
    this.loading = true;
    this.apiService.getClubSettings().subscribe({
      next: (data) => {
        this.settings = data;
        this.drafts = {};
        this.validationErrors = {};
        data.forEach((s: any) => {
          this.drafts[s.key] = JSON.stringify(s.value, null, 2);
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les paramètres.';
        this.loading = false;
      }
    });
  }

  /** Valide le brouillon à chaque frappe (bouton désactivé si invalide). */
  validate(key: string): boolean {
    try {
      const parsed = JSON.parse(this.drafts[key]);
      if (typeof parsed !== 'object' || parsed === null) {
        this.validationErrors[key] = true;
        return false;
      }
      this.validationErrors[key] = false;
      return true;
    } catch {
      this.validationErrors[key] = true;
      return false;
    }
  }

  save(key: string) {
    if (!this.validate(key)) return;
    const payload = JSON.parse(this.drafts[key]);
    this.apiService.updateClubSetting(key, payload).subscribe({
      next: () => {
        const s = this.settings.find(x => x.key === key);
        if (s) s.value = payload;
      },
      error: () => {
        alert('Échec de l\'enregistrement.');
      }
    });
  }

  labelFor(key: string): string {
    switch (key) {
      case 'membership_tiers': return "Paliers d'adhésion";
      case 'club_info': return 'Coordonnées du club';
      default: return key;
    }
  }
}
